-- ============================================================
-- V14: Analytics indexes, Notifications, Full-text search
-- ============================================================

-- ── Notifications ───────────────────────────────────────────
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(50) NOT NULL,        -- ANALYSIS_COMPLETE, CRITICAL_FINDING, SYSTEM, INFO
    title           VARCHAR(255) NOT NULL,
    message         TEXT NOT NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'INFO', -- INFO, WARNING, CRITICAL
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    related_entity_type VARCHAR(50),             -- ANALYSIS, PATIENT, WORKFLOW etc.
    related_entity_id   UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Efficient lookups: user's unread notifications
CREATE INDEX idx_notifications_user_unread
    ON notifications (tenant_id, user_id, is_read, created_at DESC)
    WHERE is_read = FALSE;

CREATE INDEX idx_notifications_user_list
    ON notifications (tenant_id, user_id, created_at DESC);

-- ── RLS for notifications ────────────────────────────────────
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;

CREATE POLICY notifications_tenant_isolation ON notifications
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::UUID);

GRANT SELECT, INSERT, UPDATE, DELETE ON notifications TO ${appDbUser};

-- ── Analytics performance indexes ───────────────────────────
-- Time-series: analyses per day
CREATE INDEX IF NOT EXISTS idx_analysis_tenant_created
    ON analysis_requests (tenant_id, created_at DESC);

-- Top diagnoses: group by analysis_type
CREATE INDEX IF NOT EXISTS idx_analysis_tenant_type
    ON analysis_requests (tenant_id, analysis_type, status);

-- Model usage: aggregate by model_used
CREATE INDEX IF NOT EXISTS idx_analysis_tenant_model
    ON analysis_requests (tenant_id, model_used)
    WHERE model_used IS NOT NULL;

-- Cost tracking
CREATE INDEX IF NOT EXISTS idx_analysis_tenant_cost
    ON analysis_requests (tenant_id, estimated_cost)
    WHERE estimated_cost IS NOT NULL;

-- ── Patient full-text search via tsvector ────────────────────
-- Add a generated tsvector column for fast full-text search
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('english',
            coalesce(first_name, '') || ' ' ||
            coalesce(last_name,  '') || ' ' ||
            coalesce(medical_record_number, '') || ' ' ||
            coalesce(phone, '') || ' ' ||
            coalesce(email, '')
        )
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_patients_search
    ON patients USING GIN (search_vector);
