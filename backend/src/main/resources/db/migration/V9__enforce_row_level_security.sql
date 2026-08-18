-- ===========================================
-- V9: Make row-level security actually enforce
-- ===========================================
-- V3 and V5 enabled RLS and wrote policies, but neither took effect at runtime:
--
--   1. The application connects as the role that OWNS these tables, and a table owner bypasses
--      RLS unless FORCE ROW LEVEL SECURITY is set. The "defence in depth" second layer was
--      doing nothing at all, while the UI advertised it.
--   2. The policies declared USING without WITH CHECK. USING filters rows that are read or
--      matched; WITH CHECK constrains rows that are written. Without it, even an enforced policy
--      would happily accept an INSERT carrying another tenant's tenant_id.
--
-- Every policy below is recreated with both clauses, plus one deliberate escape hatch:
-- app.maintenance = 'on' (transaction-local only, set by TenantSession.beginMaintenance) for the
-- two operations that are legitimately cross-tenant — looking up a refresh token by hash before
-- the tenant is known, and the analysis reaper scanning all tenants for stalled jobs.
--
-- NOTE for future migrations: FORCE applies to the owner, so any later migration that performs
-- DML on these tables must first run
--     SELECT set_config('app.maintenance', 'on', true);
-- otherwise it will silently affect zero rows.

CREATE OR REPLACE FUNCTION app_current_tenant()
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.current_tenant', true), '')::UUID
$$;

CREATE OR REPLACE FUNCTION app_tenant_visible(row_tenant_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT row_tenant_id = app_current_tenant()
        OR coalesce(current_setting('app.maintenance', true), '') = 'on'
$$;

COMMENT ON FUNCTION app_tenant_visible(UUID) IS
    'True when the row belongs to the connection''s current tenant, or when the transaction has '
    'explicitly opted into cross-tenant maintenance access.';

DO $$
DECLARE
    t TEXT;
    policy_name TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'users', 'patients', 'medical_files', 'audit_logs',
        'analysis_requests', 'refresh_tokens',
        'knowledge_documents', 'document_chunks'
    ]
    LOOP
        policy_name := 'tenant_isolation_' || t;

        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', policy_name, t);
        EXECUTE format(
            'CREATE POLICY %I ON %I FOR ALL '
            || 'USING (app_tenant_visible(tenant_id)) '
            || 'WITH CHECK (app_tenant_visible(tenant_id))',
            policy_name, t);

        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        -- The line that turns the policies from documentation into a control.
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    END LOOP;
END $$;
