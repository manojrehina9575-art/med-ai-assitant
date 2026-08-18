-- ===========================================
-- Med-AI Assistant — Add missing updated_at to document_chunks
-- ===========================================
-- BaseEntity (@UpdateTimestamp) maps an updated_at column on every entity,
-- but V5 created document_chunks with only created_at. This caused Hibernate
-- schema validation to fail on startup:
--   Schema-validation: missing column [updated_at] in table [document_chunks]

ALTER TABLE document_chunks
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
