-- ============================================================
-- V15: Compliance (Patient Consent, Retention), Model Registry & A/B Testing
-- ============================================================

-- ── 1. Patient Consent Management ────────────────────────────
CREATE TABLE patient_consents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    purpose             VARCHAR(50) NOT NULL,        -- AI_ANALYSIS, RESEARCH_USE, DATA_SHARING, MODEL_TRAINING
    status              VARCHAR(20) NOT NULL DEFAULT 'GRANTED', -- GRANTED, REVOKED, EXPIRED
    signer_name         VARCHAR(255) NOT NULL,
    signer_relationship VARCHAR(50) NOT NULL DEFAULT 'PATIENT', -- PATIENT, GUARDIAN, POWER_OF_ATTORNEY
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    signature_hash      VARCHAR(255),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_patient_purpose UNIQUE (tenant_id, patient_id, purpose)
);

CREATE INDEX idx_patient_consents_lookup
    ON patient_consents (tenant_id, patient_id, purpose, status);

ALTER TABLE patient_consents ENABLE ROW LEVEL SECURITY;
CREATE POLICY patient_consents_tenant_isolation ON patient_consents
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::UUID);
GRANT SELECT, INSERT, UPDATE, DELETE ON patient_consents TO ${appDbUser};

-- ── 2. AI Model Registry & LoRA Adapters ─────────────────────
CREATE TABLE ai_models_registry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID,                         -- NULL for global models, or specific tenant UUID for private LoRA
    model_id            VARCHAR(100) NOT NULL,        -- Unique identifier e.g. llama-3-lora-rad-v1
    display_name        VARCHAR(150) NOT NULL,
    base_model          VARCHAR(100) NOT NULL,        -- Base e.g. meta-llama/Llama-3-8B-Instruct
    adapter_type        VARCHAR(50) NOT NULL DEFAULT 'LORA', -- LORA, QLORA, FULL_FINETUNE, SYSTEM_PROMPT
    status              VARCHAR(30) NOT NULL DEFAULT 'READY', -- REGISTERED, TRAINING, READY, DEPLOYED, ARCHIVED
    lora_rank           INT,
    lora_alpha          INT,
    training_loss       DOUBLE PRECISION,
    training_samples_count INT DEFAULT 0,
    endpoint_url        VARCHAR(255),
    description         TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_models_tenant
    ON ai_models_registry (tenant_id, is_active, status);

ALTER TABLE ai_models_registry ENABLE ROW LEVEL SECURITY;
CREATE POLICY ai_models_tenant_isolation ON ai_models_registry
    USING (tenant_id IS NULL OR tenant_id = current_setting('app.current_tenant_id', TRUE)::UUID);
GRANT SELECT, INSERT, UPDATE, DELETE ON ai_models_registry TO ${appDbUser};

-- Seed initial models
INSERT INTO ai_models_registry (id, tenant_id, model_id, display_name, base_model, adapter_type, status, lora_rank, lora_alpha, training_loss, training_samples_count, description, is_active)
VALUES 
    (gen_random_uuid(), NULL, 'qwen/qwen3.6-27b', 'Qwen 2.5 Clinical Vision', 'Qwen/Qwen2.5-VL-72B', 'FULL_FINETUNE', 'DEPLOYED', NULL, NULL, 0.12, 120000, 'Production multimodal vision and text diagnostic foundation model.', TRUE),
    (gen_random_uuid(), NULL, 'llama-3.3-70b-versatile', 'Llama 3.3 70B Clinical Specialist', 'meta-llama/Llama-3.3-70B-Instruct', 'FULL_FINETUNE', 'DEPLOYED', NULL, NULL, 0.09, 250000, 'High-reasoning general clinical assistant and workflow orchestrator.', TRUE),
    (gen_random_uuid(), NULL, 'lora-radiology-xray-v1', 'LoRA Radiology X-Ray Specialist', 'meta-llama/Llama-3-8B-Instruct', 'LORA', 'READY', 16, 32, 0.18, 14200, 'Fine-tuned LoRA adapter specialized for chest X-ray consolidation & effusion grading.', TRUE),
    (gen_random_uuid(), NULL, 'lora-hematology-labs-v1', 'LoRA Lab Report Reasoning', 'meta-llama/Llama-3-8B-Instruct', 'LORA', 'READY', 8, 16, 0.14, 8500, 'Fine-tuned LoRA adapter specialized for multi-analyte blood panel interpretation.', TRUE);

-- ── 3. A/B Testing Experiments ──────────────────────────────
CREATE TABLE ab_experiments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    description             TEXT,
    model_a_id             VARCHAR(100) NOT NULL,
    model_b_id             VARCHAR(100) NOT NULL,
    traffic_split_percent   INT NOT NULL DEFAULT 50, -- Percentage directed to Model B (0-100)
    modality                VARCHAR(50) NOT NULL DEFAULT 'ALL', -- ALL, RADIOLOGY, BLOOD_LAB, CHAT
    status                  VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PAUSED, COMPLETED
    start_date              TIMESTAMPTZ NOT NULL DEFAULT now(),
    end_date                TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ab_experiments_tenant
    ON ab_experiments (tenant_id, status, modality);

ALTER TABLE ab_experiments ENABLE ROW LEVEL SECURITY;
CREATE POLICY ab_experiments_tenant_isolation ON ab_experiments
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::UUID);
GRANT SELECT, INSERT, UPDATE, DELETE ON ab_experiments TO ${appDbUser};

-- ── 4. A/B Testing Evaluations & Feedback ────────────────────
CREATE TABLE ab_experiment_evaluations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    experiment_id       UUID NOT NULL REFERENCES ab_experiments(id) ON DELETE CASCADE,
    assigned_variant    VARCHAR(10) NOT NULL,        -- 'A' or 'B'
    model_used          VARCHAR(100) NOT NULL,
    latency_ms          BIGINT,
    token_count         INT,
    user_rating         INT,                         -- 1 to 5 stars
    is_accurate         BOOLEAN,
    feedback_notes      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ab_evaluations_exp
    ON ab_experiment_evaluations (tenant_id, experiment_id, assigned_variant);

ALTER TABLE ab_experiment_evaluations ENABLE ROW LEVEL SECURITY;
CREATE POLICY ab_experiment_evaluations_tenant_isolation ON ab_experiment_evaluations
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::UUID);
GRANT SELECT, INSERT, UPDATE, DELETE ON ab_experiment_evaluations TO ${appDbUser};

-- ── 5. Data Retention Policies ──────────────────────────────
CREATE TABLE data_retention_policies (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL UNIQUE,
    audit_log_retention_days    INT NOT NULL DEFAULT 365,
    analysis_retention_days     INT NOT NULL DEFAULT 730,
    chat_session_retention_days INT NOT NULL DEFAULT 180,
    soft_delete_purge_days      INT NOT NULL DEFAULT 30,
    is_auto_purge_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    last_purge_at               TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE data_retention_policies ENABLE ROW LEVEL SECURITY;
CREATE POLICY retention_policies_tenant_isolation ON data_retention_policies
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::UUID);
GRANT SELECT, INSERT, UPDATE, DELETE ON data_retention_policies TO ${appDbUser};

-- ── 6. Retention Purge Execution Logs ────────────────────────
CREATE TABLE retention_purge_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    entity_type             VARCHAR(50) NOT NULL,
    records_purged_count    INT NOT NULL DEFAULT 0,
    status                  VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    error_details           TEXT,
    executed_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_retention_purge_logs
    ON retention_purge_logs (tenant_id, executed_at DESC);

ALTER TABLE retention_purge_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY retention_purge_logs_tenant_isolation ON retention_purge_logs
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::UUID);
GRANT SELECT, INSERT, UPDATE, DELETE ON retention_purge_logs TO ${appDbUser};
