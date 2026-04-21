-- MVP 2: Medical Image Analysis tables

CREATE TABLE analysis_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    patient_id UUID NOT NULL REFERENCES patients(id),
    medical_file_id UUID NOT NULL REFERENCES medical_files(id),
    requested_by UUID NOT NULL REFERENCES users(id),
    analysis_type VARCHAR(50) NOT NULL DEFAULT 'IMAGE_ANALYSIS',
    clinical_notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    urgency VARCHAR(20),
    -- Structured result stored as JSONB
    result JSONB,
    error_message TEXT,
    -- AI model tracking
    model_used VARCHAR(100),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    estimated_cost DECIMAL(10, 6),
    -- Timing
    processing_started_at TIMESTAMP WITH TIME ZONE,
    processing_completed_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_requests_tenant ON analysis_requests(tenant_id);
CREATE INDEX idx_analysis_requests_patient ON analysis_requests(patient_id);
CREATE INDEX idx_analysis_requests_file ON analysis_requests(medical_file_id);
CREATE INDEX idx_analysis_requests_status ON analysis_requests(status);
CREATE INDEX idx_analysis_requests_created ON analysis_requests(created_at DESC);

CREATE TRIGGER update_analysis_requests_updated_at
    BEFORE UPDATE ON analysis_requests
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
