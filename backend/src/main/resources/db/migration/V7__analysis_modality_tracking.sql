-- ===========================================
-- V7: Record which modality the model actually received
-- ===========================================
-- Previously, if a multimodal (vision) call failed for any reason, the analysis
-- services silently retried with a text-only prompt containing just the filename.
-- The model then produced findings, severities, confidence scores, and ICD-10 codes
-- for a study it had never seen, and the row was stored as COMPLETED.
--
-- That fallback is gone. This column makes the remaining guarantee auditable:
-- a completed analysis records how its input actually reached the model.
--   VISION — the model was given pixels (image, DICOM frame, or rendered PDF page)
--   TEXT   — the model was given text extracted from the document

ALTER TABLE analysis_requests
    ADD COLUMN modality_used VARCHAR(20);

COMMENT ON COLUMN analysis_requests.modality_used IS
    'How the input reached the model: VISION (pixels) or TEXT (extracted document text). '
    'NULL for rows created before V7.';

-- Analyses left PENDING by the old code are unreachable: nothing ever re-read that status.
-- The reaper introduced alongside this migration will now pick up PENDING rows, so any
-- stale ones from before this deploy are retired here rather than silently resurrected.
UPDATE analysis_requests
   SET status = 'FAILED',
       error_message = COALESCE(error_message, 'Retired by V7: superseded by durable retry handling.'),
       processing_completed_at = COALESCE(processing_completed_at, NOW())
 WHERE status IN ('PENDING', 'PROCESSING')
   AND created_at < NOW() - INTERVAL '1 hour';
