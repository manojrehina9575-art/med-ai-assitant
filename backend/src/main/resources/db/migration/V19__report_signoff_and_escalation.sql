-- ===========================================
-- V19: Clinician sign-off, feedback capture, critical-results escalation
-- ===========================================
-- An analysis result was terminal: it was generated and it sat there. Every real radiology and
-- pathology product is built around draft -> review -> sign, and its absence here costs three
-- things at once:
--
--   1. Clinically, nobody owns the output. A report nobody signed is a report nobody acts on.
--   2. Legally, the "the product drafts, a clinician decides" position was a claim in a document
--      rather than something the software enforced. This is what makes it true in the code.
--   3. Commercially, the accept/edit/reject signal a reviewer produces is the training data. The
--      fine-tuning pipeline has existed since MVP 8 with nothing feeding it.
--
-- One table serves all three, because they are one interaction. A reviewer accepting, correcting
-- or rejecting a draft is simultaneously the sign-off, the audit record and the training label;
-- modelling them separately would mean three writes that can disagree with each other.

CREATE TABLE report_reviews (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    analysis_id         UUID NOT NULL REFERENCES analysis_requests(id) ON DELETE CASCADE,
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,

    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'IN_REVIEW', 'SIGNED', 'REJECTED', 'AMENDED')),

    -- Who currently holds it. Claiming is advisory rather than a lock: two radiologists opening
    -- the same study is a workflow problem to surface, not a transaction to serialise.
    claimed_by          UUID REFERENCES users(id),
    claimed_at          TIMESTAMPTZ,

    signed_by           UUID REFERENCES users(id),
    signed_at           TIMESTAMPTZ,

    /*
     * The training signal, and the reason this is one table rather than three.
     *
     * ACCEPTED  — the draft was correct as generated. A positive example.
     * EDITED    — the clinician corrected it. final_content against the draft is the most
     *             valuable pair in the whole system: a real error and its real correction.
     * REJECTED  — the draft was unusable. rejection_reason says why.
     */
    review_action       VARCHAR(20)
                        CHECK (review_action IN ('ACCEPTED', 'EDITED', 'REJECTED')),
    rejection_reason    TEXT,

    -- What the model produced, frozen at review time. The analysis row can be retried and
    -- overwritten; what the clinician actually saw must not move under them afterwards.
    draft_content       TEXT,
    -- What the clinician signed. Equal to draft_content for an ACCEPTED review.
    final_content       TEXT,

    -- Set when a signed report is superseded. A signed report is never edited in place: an
    -- amendment is a new review, and both remain readable.
    amends_review_id    UUID REFERENCES report_reviews(id),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A signature is worthless without an identity and a time behind it.
    CONSTRAINT chk_signed_is_attributed
        CHECK (status <> 'SIGNED' OR (signed_by IS NOT NULL AND signed_at IS NOT NULL AND review_action IS NOT NULL)),
    CONSTRAINT chk_rejection_has_reason
        CHECK (review_action <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

-- One open review per analysis. An amendment supersedes rather than duplicates, so the partial
-- index covers only the states that are still live.
CREATE UNIQUE INDEX uq_report_reviews_open
    ON report_reviews (analysis_id)
    WHERE status IN ('DRAFT', 'IN_REVIEW');

CREATE INDEX idx_report_reviews_worklist
    ON report_reviews (tenant_id, status, created_at DESC);
CREATE INDEX idx_report_reviews_patient
    ON report_reviews (tenant_id, patient_id, created_at DESC);
-- Supports the training-data export: signed reviews carrying a correction.
CREATE INDEX idx_report_reviews_training
    ON report_reviews (tenant_id, review_action, signed_at DESC)
    WHERE status = 'SIGNED';

ALTER TABLE report_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_reviews FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_report_reviews ON report_reviews
    USING (app_tenant_visible(tenant_id))
    WITH CHECK (app_tenant_visible(tenant_id));

-- ── Critical-results escalation ──────────────────────────────────────────────
--
-- The guardrail detected an acute red flag and then rendered a banner. In most jurisdictions a
-- critical finding carries a documented notification-and-acknowledgement duty: someone must be
-- told, and the fact that they were told must be recorded. A banner on a screen nobody was
-- looking at satisfies neither.

CREATE TABLE critical_result_escalations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    analysis_id         UUID NOT NULL REFERENCES analysis_requests(id) ON DELETE CASCADE,
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,

    urgency             VARCHAR(20) NOT NULL CHECK (urgency IN ('URGENT', 'CRITICAL')),
    finding_summary     TEXT NOT NULL,

    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'CLOSED')),

    -- Rises each time the acknowledgement deadline passes without one. Level 0 is the requesting
    -- clinician; higher levels widen to every doctor and then to administrators.
    escalation_level    SMALLINT NOT NULL DEFAULT 0,
    last_notified_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    acknowledged_by     UUID REFERENCES users(id),
    acknowledged_at     TIMESTAMPTZ,
    -- What the clinician did about it. An acknowledgement with no action recorded is a click.
    action_taken        TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_ack_is_attributed
        CHECK (status <> 'ACKNOWLEDGED' OR (acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL)),
    -- One live escalation per analysis; re-running an analysis does not re-page the ward.
    CONSTRAINT uq_escalation_analysis UNIQUE (analysis_id)
);

CREATE INDEX idx_escalations_open
    ON critical_result_escalations (status, last_notified_at)
    WHERE status = 'OPEN';
CREATE INDEX idx_escalations_tenant
    ON critical_result_escalations (tenant_id, created_at DESC);

ALTER TABLE critical_result_escalations ENABLE ROW LEVEL SECURITY;
ALTER TABLE critical_result_escalations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_critical_result_escalations ON critical_result_escalations
    USING (app_tenant_visible(tenant_id))
    WITH CHECK (app_tenant_visible(tenant_id));

-- ── Model abstention ─────────────────────────────────────────────────────────
--
-- Every input got an answer. A decision-support system that can say "image quality insufficient"
-- or "outside my validated scope" is trusted more, not less, and it removes the failure mode most
-- likely to end up in a case report.

ALTER TABLE analysis_requests
    ADD COLUMN abstained BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN abstention_reason TEXT;

COMMENT ON COLUMN analysis_requests.abstained IS
    'True when the model declined to produce findings. Distinct from FAILED, which means the call '
    'did not complete — an abstention is a successful call with a considered refusal, and is never '
    'billed or used as training data.';
