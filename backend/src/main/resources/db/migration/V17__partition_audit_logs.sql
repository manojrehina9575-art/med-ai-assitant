-- ===========================================
-- V17: Partition audit_logs by month
-- ===========================================
-- Every controller call writes an audit row, reads included, and V16 made the table append-only
-- with a six-year floor. Those two facts together mean audit_logs is on course to be the largest
-- object in the database and to stay that way: one flat heap, one ever-growing B-tree per index,
-- and a purge that has to scan the whole thing to find the rows old enough to remove.
--
-- Monthly range partitions fix all three. Queries carry a created_at predicate (the repository
-- orders by it and the retention purge filters on it), so the planner prunes to a handful of
-- partitions; each index stays the size of one month; and a purge becomes a bounded delete over
-- the oldest partitions rather than a full scan.
--
-- The partition key has to be part of the primary key, so the key becomes (id, created_at). Ids
-- are still UUIDs and still unique in practice; nothing looks a row up by id alone.

ALTER TABLE audit_logs RENAME TO audit_logs_legacy;

-- Renaming carries the indexes and policies along with the table, and the new table needs its own.
DROP POLICY IF EXISTS tenant_isolation_audit_logs ON audit_logs_legacy;

CREATE TABLE audit_logs (
    id           UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id      UUID REFERENCES users(id),
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    UUID,
    details      JSONB DEFAULT '{}',
    ip_address   VARCHAR(45),
    user_agent   TEXT,
    -- NOT NULL is required of a partition key, and was already the entity's contract.
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE audit_logs IS
    'Append-only audit trail, partitioned by month. The application role holds SELECT and INSERT '
    'only (V16); removal goes through purge_audit_logs(), which archives first and refuses to '
    'delete anything under six years old.';

/*
 * Creates one month's partition, and makes it as locked-down as its parent.
 *
 * SECURITY DEFINER because partition creation is DDL and the application role deliberately has
 * none — the scheduler that keeps partitions ahead of the clock runs as the application.
 *
 * Row-level security is applied to the partition itself, not just inherited through the parent.
 * A policy on the parent governs access *via* the parent, but a partition is a table in its own
 * right and can be queried directly by name; without this, audit_logs_2026_08 would be a way
 * around tenant isolation for any role holding SELECT on it.
 */
CREATE OR REPLACE FUNCTION ensure_audit_partition(p_month DATE)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    start_ts  TIMESTAMPTZ := date_trunc('month', p_month::timestamptz);
    end_ts    TIMESTAMPTZ := date_trunc('month', p_month::timestamptz) + INTERVAL '1 month';
    part_name TEXT := 'audit_logs_' || to_char(start_ts, 'YYYY_MM');
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = part_name AND relkind = 'r') THEN
        RETURN part_name;
    END IF;

    BEGIN
        EXECUTE format('CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
                       part_name, start_ts, end_ts);
    EXCEPTION WHEN check_violation THEN
        -- Rows for this month are already sitting in the default partition, which blocks the
        -- attach. Detach it, carve out the month, put the rest back. The default exists only so
        -- that a late partition can never cause an audit write to fail; it should stay empty, and
        -- reaching this branch means the scheduler fell behind.
        RAISE NOTICE 'Default audit partition holds rows for %; migrating them into %', start_ts, part_name;
        EXECUTE 'ALTER TABLE audit_logs DETACH PARTITION audit_logs_default';
        EXECUTE format('CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
                       part_name, start_ts, end_ts);
        EXECUTE format('WITH moved AS (DELETE FROM audit_logs_default '
                       || 'WHERE created_at >= %L AND created_at < %L RETURNING *) '
                       || 'INSERT INTO %I SELECT * FROM moved', start_ts, end_ts, part_name);
        EXECUTE 'ALTER TABLE audit_logs ATTACH PARTITION audit_logs_default DEFAULT';
    END;

    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', part_name);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', part_name);
    EXECUTE format('CREATE POLICY tenant_isolation_%1$s ON %1$I '
                   || 'USING (app_tenant_visible(tenant_id)) '
                   || 'WITH CHECK (app_tenant_visible(tenant_id))', part_name);

    -- Matches the parent: readable and appendable by the application, never editable or erasable.
    EXECUTE format('REVOKE ALL ON %I FROM ${appDbUser}', part_name);
    EXECUTE format('GRANT SELECT, INSERT ON %I TO ${appDbUser}', part_name);

    RETURN part_name;
END $$;

REVOKE ALL ON FUNCTION ensure_audit_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ensure_audit_partition(DATE) TO ${appDbUser};

-- The catch-all. An audit write must never fail because nobody created next month's partition,
-- so there is always somewhere for a row to land; AuditPartitionMaintenance keeps real partitions
-- far enough ahead that this stays empty.
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

ALTER TABLE audit_logs_default ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs_default FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_audit_logs_default ON audit_logs_default
    USING (app_tenant_visible(tenant_id))
    WITH CHECK (app_tenant_visible(tenant_id));

-- Partitions covering everything already recorded, plus three months of headroom.
DO $$
DECLARE
    cursor_month DATE;
    last_month   DATE;
BEGIN
    SELECT date_trunc('month', COALESCE(MIN(created_at), now()))::date
      INTO cursor_month FROM audit_logs_legacy;

    last_month := (date_trunc('month', now()) + INTERVAL '3 months')::date;

    WHILE cursor_month <= last_month LOOP
        PERFORM ensure_audit_partition(cursor_month);
        cursor_month := (cursor_month + INTERVAL '1 month')::date;
    END LOOP;
END $$;

-- Move the existing trail across. Routed through the parent so each row lands in its own month.
INSERT INTO audit_logs (id, tenant_id, user_id, action, entity_type, entity_id,
                        details, ip_address, user_agent, created_at)
SELECT id, tenant_id, user_id, action, entity_type, entity_id,
       details, ip_address, user_agent, COALESCE(created_at, now())
FROM audit_logs_legacy;

DROP TABLE audit_logs_legacy;

-- Indexes are declared on the parent, so every present and future partition gets its own local
-- copy — each covering one month rather than the whole history.
CREATE INDEX idx_audit_logs_tenant_created ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX idx_audit_logs_tenant_user    ON audit_logs (tenant_id, user_id, created_at DESC);
CREATE INDEX idx_audit_logs_tenant_entity  ON audit_logs (tenant_id, entity_type, created_at DESC);

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_audit_logs ON audit_logs
    USING (app_tenant_visible(tenant_id))
    WITH CHECK (app_tenant_visible(tenant_id));

-- Reassert V16's split: the application appends and reads, and cannot rewrite history.
REVOKE ALL ON audit_logs FROM ${appDbUser};
GRANT SELECT, INSERT ON audit_logs TO ${appDbUser};
REVOKE ALL ON audit_logs_default FROM ${appDbUser};
GRANT SELECT, INSERT ON audit_logs_default TO ${appDbUser};
