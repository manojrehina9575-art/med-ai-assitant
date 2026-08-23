-- ===========================================
-- V18: Turn metered usage into invoices
-- ===========================================
-- tenant_ai_usage_daily has held accurate per-tenant token counts and provider cost since V10, and
-- nothing has ever read it for money. This is the table that turns it into revenue.
--
-- One distinction runs through the whole design: cost_usd is what the AI provider charges *us*.
-- It is cost of goods, not price. Invoices are built from billable units at plan rates; provider
-- cost is carried alongside so margin is visible, and is never what a customer is shown.
--
-- The billable unit is the analysis, not the AI request. A chat turn and a chest X-ray report are
-- both "an AI request" and are worth very different amounts; diagnostic chains already think in
-- studies, so that is what they are billed for.

-- ── 1. Plan catalogue ────────────────────────────────────────────────────────
--
-- Global reference data rather than tenant-scoped: a plan is the platform's offer, not a tenant's
-- record. No RLS, and the application role gets SELECT only — a tenant admin must not be able to
-- edit the rate card they are billed against.

CREATE TABLE billing_plans (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                    VARCHAR(50) NOT NULL UNIQUE,
    display_name            VARCHAR(150) NOT NULL,
    currency                VARCHAR(3) NOT NULL DEFAULT 'INR',
    -- Recurring fee charged whether or not anything is used.
    platform_fee            NUMERIC(12,2) NOT NULL DEFAULT 0,
    -- Analyses covered by the platform fee before overage applies.
    included_analyses       INT NOT NULL DEFAULT 0,
    price_per_analysis      NUMERIC(12,4) NOT NULL DEFAULT 0,
    -- Per-seat pricing, for hospitals where budget sits with IT rather than operations.
    price_per_active_seat   NUMERIC(12,2) NOT NULL DEFAULT 0,
    -- India: GST on SaaS is 18%. Held per plan so a plan sold outside India can carry its own.
    tax_rate_percent        NUMERIC(5,2) NOT NULL DEFAULT 18.00,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

REVOKE ALL ON billing_plans FROM ${appDbUser};
GRANT SELECT ON billing_plans TO ${appDbUser};

COMMENT ON TABLE billing_plans IS
    'Platform rate card. Read-only to the application: a tenant must not be able to edit the '
    'terms it is invoiced against.';

INSERT INTO billing_plans
    (code, display_name, currency, platform_fee, included_analyses, price_per_analysis, price_per_active_seat)
VALUES
    -- Priced for the segments the go-to-market names: volume for teleradiology, seats for hospitals.
    ('PILOT',        'Paid pilot (8 weeks)',        'INR',      0,   500,  0.00,   0.00),
    ('VOLUME',       'Per-study (diagnostic chains)','INR',      0,     0, 35.0000, 0.00),
    ('HOSPITAL',     'Platform + seats',            'INR', 75000,  2000, 20.0000, 1500.00);

-- ── 2. Subscriptions ─────────────────────────────────────────────────────────

CREATE TABLE tenant_subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    plan_id             UUID NOT NULL REFERENCES billing_plans(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED')),
    -- Day of month the billing period closes. Anchored per tenant so invoicing is not a
    -- month-end thundering herd across the whole customer base.
    billing_day         SMALLINT NOT NULL DEFAULT 1 CHECK (billing_day BETWEEN 1 AND 28),
    started_on          DATE NOT NULL DEFAULT CURRENT_DATE,
    cancelled_on        DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_subscriptions_status ON tenant_subscriptions (status, billing_day);

ALTER TABLE tenant_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_subscriptions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tenant_subscriptions ON tenant_subscriptions
    USING (app_tenant_visible(tenant_id))
    WITH CHECK (app_tenant_visible(tenant_id));

-- ── 3. Invoices ──────────────────────────────────────────────────────────────

CREATE TABLE invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_number      VARCHAR(40) NOT NULL UNIQUE,
    plan_code           VARCHAR(50) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    subtotal            NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_rate_percent    NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    total               NUMERIC(14,2) NOT NULL DEFAULT 0,
    -- What the AI providers charged us for this period. Margin visibility, never shown to the
    -- customer, and deliberately in USD because that is the currency providers bill in.
    provider_cost_usd   NUMERIC(14,6) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'VOID')),
    issued_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Regenerating a period must update the existing draft, not add a second invoice for it.
    CONSTRAINT uq_invoice_tenant_period UNIQUE (tenant_id, period_start, period_end)
);

CREATE INDEX idx_invoices_tenant_period ON invoices (tenant_id, period_start DESC);

ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoices FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_invoices ON invoices
    USING (app_tenant_visible(tenant_id))
    WITH CHECK (app_tenant_visible(tenant_id));

CREATE TABLE invoice_line_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id      UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description     VARCHAR(200) NOT NULL,
    quantity        NUMERIC(12,2) NOT NULL DEFAULT 0,
    unit_price      NUMERIC(12,4) NOT NULL DEFAULT 0,
    amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    sort_order      SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_invoice_line_items_invoice ON invoice_line_items (invoice_id, sort_order);

ALTER TABLE invoice_line_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoice_line_items FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_invoice_line_items ON invoice_line_items
    USING (app_tenant_visible(tenant_id))
    WITH CHECK (app_tenant_visible(tenant_id));

-- Sequence for human-readable invoice numbers. A UUID is not something an accounts department
-- can quote down a phone line.
CREATE SEQUENCE invoice_number_seq START 1000;
GRANT USAGE, SELECT ON SEQUENCE invoice_number_seq TO ${appDbUser};
