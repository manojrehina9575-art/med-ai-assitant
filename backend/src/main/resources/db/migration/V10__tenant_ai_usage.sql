-- ===========================================
-- V10: Persist per-tenant AI spend
-- ===========================================
-- The daily cost cap was held in a ConcurrentHashMap, which meant a restart reset every tenant's
-- spend to zero and a second application instance enforced its own separate limit. Persisting it
-- makes the cap real — and gives billing something to read.

CREATE TABLE tenant_ai_usage_daily (
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    usage_date        DATE NOT NULL,
    request_count     INTEGER NOT NULL DEFAULT 0,
    prompt_tokens     BIGINT  NOT NULL DEFAULT 0,
    completion_tokens BIGINT  NOT NULL DEFAULT 0,
    cost_usd          NUMERIC(12, 6) NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, usage_date)
);

CREATE INDEX idx_tenant_ai_usage_date ON tenant_ai_usage_daily(usage_date);

ALTER TABLE tenant_ai_usage_daily ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_ai_usage_daily FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_tenant_ai_usage_daily ON tenant_ai_usage_daily
    FOR ALL
    USING (app_tenant_visible(tenant_id))
    WITH CHECK (app_tenant_visible(tenant_id));

COMMENT ON TABLE tenant_ai_usage_daily IS
    'Per-tenant, per-day AI usage and estimated spend. Written after each completed analysis and '
    'read by the daily cost cap.';
