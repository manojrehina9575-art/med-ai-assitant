-- =============================================
-- V4: Refresh token storage for token rotation
-- Enables revocation and one-time-use rotation
-- =============================================

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by UUID REFERENCES refresh_tokens(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_tenant ON refresh_tokens(tenant_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);

-- Enable RLS on refresh_tokens as well
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_refresh_tokens ON refresh_tokens
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID);
