-- =============================================
-- V3: Row-Level Security for tenant isolation
-- Adds a database-level safety net so that even
-- if application code forgets a tenant_id filter,
-- PostgreSQL will enforce isolation.
-- =============================================

-- Enable RLS on all tenant-scoped tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE patients ENABLE ROW LEVEL SECURITY;
ALTER TABLE medical_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE analysis_requests ENABLE ROW LEVEL SECURITY;

-- Create policies that use the session variable 'app.current_tenant'
-- The application sets this via: SET LOCAL app.current_tenant = '<tenant-uuid>';

-- Users policy
CREATE POLICY tenant_isolation_users ON users
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID);

-- Patients policy
CREATE POLICY tenant_isolation_patients ON patients
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID);

-- Medical files policy
CREATE POLICY tenant_isolation_medical_files ON medical_files
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID);

-- Audit logs policy
CREATE POLICY tenant_isolation_audit_logs ON audit_logs
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID);

-- Analysis requests policy
CREATE POLICY tenant_isolation_analysis_requests ON analysis_requests
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID);

-- IMPORTANT: The superuser / table owner bypasses RLS by default.
-- For production, the application should connect with a non-owner role.
-- To force RLS even for the table owner (useful in dev), uncomment:
-- ALTER TABLE users FORCE ROW LEVEL SECURITY;
-- ALTER TABLE patients FORCE ROW LEVEL SECURITY;
-- ALTER TABLE medical_files FORCE ROW LEVEL SECURITY;
-- ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
-- ALTER TABLE analysis_requests FORCE ROW LEVEL SECURITY;
