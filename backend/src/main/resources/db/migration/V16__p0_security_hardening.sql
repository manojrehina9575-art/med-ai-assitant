-- ===========================================
-- V16: P0 security hardening
-- ===========================================
-- Three unrelated defects, all of which had to be fixed in the database:
--
--   1. Thirteen row-level-security policies added by V13-V15 test against
--      current_setting('app.current_tenant_id'). Nothing sets that name — TenantSession and V9
--      both use 'app.current_tenant'. current_setting(..., TRUE) returns NULL for an unset name,
--      so every one of those policies evaluated to NULL and the application role could neither
--      read nor write the tables. Prescriptions, lab orders, appointments, agent workflows,
--      notifications, consent records and the retention policies themselves were all unreachable
--      in any deployment where the app connects as medai_app (which is every deployment since
--      V11). They also declared USING without WITH CHECK, the same gap V9 closed for the earlier
--      tables, and none of them were FORCEd.
--
--   2. Audit logs were deletable by the application role, with a tenant-configurable retention
--      defaulting to 365 days. HIPAA 164.316(b)(2)(i) requires six years. The audit trail is also
--      the first artefact a regulator asks for, so "the application can delete it" is itself the
--      finding.
--
--   3. Prescriptions recorded no safety verdict, so a contraindicated prescription and a clean one
--      were indistinguishable after the fact.

-- ── 1. Repair the broken tenant policies ─────────────────────────────────────
--
-- Rebuilt on app_tenant_visible() from V9, so there is now exactly one definition of "this row
-- belongs to the caller's tenant" in the schema, and the maintenance escape hatch works uniformly.

DO $$
DECLARE
    t TEXT;
    policy_name TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'patient_consents',
        'ab_experiments',
        'ab_experiment_evaluations',
        'data_retention_policies',
        'retention_purge_logs',
        'appointments',
        'prescriptions',
        'lab_orders',
        'agent_workflows',
        'agent_workflow_steps',
        'tool_executions',
        'notifications'
    ]
    LOOP
        -- Drop whatever policies exist rather than naming them: V13-V15 used inconsistent names.
        FOR policy_name IN
            SELECT policyname FROM pg_policies WHERE schemaname = 'public' AND tablename = t
        LOOP
            EXECUTE format('DROP POLICY %I ON public.%I', policy_name, t);
        END LOOP;

        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($f$
            CREATE POLICY tenant_isolation_%1$s ON public.%1$I
                USING (app_tenant_visible(tenant_id))
                WITH CHECK (app_tenant_visible(tenant_id))
        $f$, t);
    END LOOP;
END $$;

-- ai_models_registry is the one exception: a NULL tenant_id means a platform-wide base model that
-- every tenant may use, so it cannot go through the loop above.
DO $$
DECLARE
    policy_name TEXT;
BEGIN
    FOR policy_name IN
        SELECT policyname FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'ai_models_registry'
    LOOP
        EXECUTE format('DROP POLICY %I ON public.ai_models_registry', policy_name);
    END LOOP;
END $$;

ALTER TABLE ai_models_registry ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_models_registry FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_ai_models_registry ON ai_models_registry
    USING (tenant_id IS NULL OR app_tenant_visible(tenant_id))
    WITH CHECK (tenant_id IS NULL OR app_tenant_visible(tenant_id));

COMMENT ON POLICY tenant_isolation_ai_models_registry ON ai_models_registry IS
    'NULL tenant_id is a shared platform model, readable and registrable by any tenant. '
    'Registration is separately restricted to HOSPITAL_ADMIN in ModelRegistryController.';

-- ── 2. Make the audit trail append-only, with a six-year floor ───────────────

CREATE TABLE audit_log_archive (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    user_id       UUID,
    action        VARCHAR(100) NOT NULL,
    entity_type   VARCHAR(100) NOT NULL,
    entity_id     UUID,
    details       JSONB DEFAULT '{}',
    ip_address    VARCHAR(45),
    user_agent    TEXT,
    created_at    TIMESTAMPTZ,
    archived_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_archive_tenant ON audit_log_archive (tenant_id, created_at DESC);

COMMENT ON TABLE audit_log_archive IS
    'Audit rows moved out of audit_logs by purge_audit_logs(). The application role has no '
    'privileges here at all: only the SECURITY DEFINER purge function writes, and export for '
    'long-term retention runs under the owner account. Nothing the application can be tricked '
    'into doing destroys an audit record.';

-- The application may write audit rows and read them back. It may not alter or remove them.
REVOKE UPDATE, DELETE ON audit_logs FROM ${appDbUser};
REVOKE ALL ON audit_log_archive FROM ${appDbUser};

-- Belt and braces: if some later migration re-runs a blanket GRANT ON ALL TABLES, the archive
-- still admits nobody. Deliberately ENABLE without FORCE — purge_audit_logs() runs as the owner,
-- and FORCE would apply the deny-all policy to the owner too, which would break the one writer
-- this table is supposed to have.
ALTER TABLE audit_log_archive ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_log_archive_deny_all ON audit_log_archive USING (false) WITH CHECK (false);

/*
 * Purges one tenant's audit logs, archiving first and refusing to go below the statutory floor.
 *
 * SECURITY DEFINER because the application role deliberately has no DELETE on audit_logs — this
 * function is the only path, and it enforces the rules the application cannot be trusted to.
 * A caller asking for a 30-day retention gets a six-year purge, silently and correctly.
 */
CREATE OR REPLACE FUNCTION purge_audit_logs(p_tenant_id UUID, p_cutoff TIMESTAMPTZ)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    effective_cutoff TIMESTAMPTZ;
    purged INT;
BEGIN
    IF p_tenant_id IS NULL THEN
        RAISE EXCEPTION 'purge_audit_logs requires a tenant id';
    END IF;

    -- HIPAA 164.316(b)(2)(i): six years. Whatever the tenant configured, nothing newer goes.
    effective_cutoff := LEAST(p_cutoff, now() - INTERVAL '6 years');

    WITH moved AS (
        DELETE FROM audit_logs
        WHERE tenant_id = p_tenant_id
          AND created_at < effective_cutoff
        RETURNING *
    )
    INSERT INTO audit_log_archive (
        id, tenant_id, user_id, action, entity_type, entity_id,
        details, ip_address, user_agent, created_at
    )
    SELECT id, tenant_id, user_id, action, entity_type, entity_id,
           details, ip_address, user_agent, created_at
    FROM moved;

    GET DIAGNOSTICS purged = ROW_COUNT;
    RETURN purged;
END $$;

REVOKE ALL ON FUNCTION purge_audit_logs(UUID, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION purge_audit_logs(UUID, TIMESTAMPTZ) TO ${appDbUser};

COMMENT ON FUNCTION purge_audit_logs(UUID, TIMESTAMPTZ) IS
    'Archives then deletes audit rows older than the cutoff, clamped to a minimum age of six '
    'years. The only route by which the application can remove an audit record.';

-- ── 3. Retention policy floor ────────────────────────────────────────────────
--
-- The old default of 365 days was not merely short, it was unreachable: the purge issued
-- DELETE ... WHERE timestamp < ?, and the column is created_at. Every audit purge since V15 threw
-- and was swallowed into a FAILED row in retention_purge_logs. Correcting the column name without
-- also raising the floor would have turned a silent no-op into a silent six-year data loss.

UPDATE data_retention_policies
SET audit_log_retention_days = 2190
WHERE audit_log_retention_days < 2190;

ALTER TABLE data_retention_policies
    ALTER COLUMN audit_log_retention_days SET DEFAULT 2190;

ALTER TABLE data_retention_policies
    ADD CONSTRAINT chk_audit_retention_statutory_floor
    CHECK (audit_log_retention_days >= 2190);

-- ── 4. Record the safety verdict on every prescription ───────────────────────

ALTER TABLE prescriptions
    ADD COLUMN safety_status VARCHAR(30) NOT NULL DEFAULT 'CLEAR',
    ADD COLUMN safety_findings JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN acknowledged_by UUID,
    ADD COLUMN acknowledged_at TIMESTAMPTZ;

ALTER TABLE prescriptions
    ADD CONSTRAINT chk_prescription_safety_status
    CHECK (safety_status IN ('CLEAR', 'WARNING', 'OVERRIDDEN'));

-- A prescription that carried a contraindication must record who accepted it. There is no
-- CONTRAINDICATED state: DrugSafetyService refuses to produce the row at all in that case, so
-- OVERRIDDEN is the strongest thing that can reach the table, and it is never anonymous.
ALTER TABLE prescriptions
    ADD CONSTRAINT chk_prescription_override_attributed
    CHECK (safety_status <> 'OVERRIDDEN' OR (acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL));

COMMENT ON COLUMN prescriptions.safety_findings IS
    'DrugSafetyService findings as of the moment of writing, retained verbatim. What the checker '
    'knew then is the question asked afterwards, and it is not answerable by re-running it later '
    'against a changed knowledge base.';
