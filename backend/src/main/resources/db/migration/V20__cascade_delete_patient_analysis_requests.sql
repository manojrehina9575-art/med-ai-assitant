-- ==========================================================
-- V20: Add ON DELETE CASCADE to analysis_requests constraints
-- Ensures hard-deleting a patient or medical file cascades
-- ==========================================================

ALTER TABLE analysis_requests
    DROP CONSTRAINT IF EXISTS analysis_requests_patient_id_fkey,
    ADD CONSTRAINT analysis_requests_patient_id_fkey
        FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE;

ALTER TABLE analysis_requests
    DROP CONSTRAINT IF EXISTS analysis_requests_medical_file_id_fkey,
    ADD CONSTRAINT analysis_requests_medical_file_id_fkey
        FOREIGN KEY (medical_file_id) REFERENCES medical_files(id) ON DELETE CASCADE;
