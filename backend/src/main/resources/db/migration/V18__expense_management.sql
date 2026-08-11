-- Gap-analysis #10 (product-requirements.md Section 16): gider yönetimi.
-- expense_category: business-scoped, manuel yönetim.
-- expense: DRAFT/SUBMITTED editable, APPROVED/REJECTED sonrası immutable (uygulama katmanında zorlanıyor).
-- recurring_expense_template: MONTHLY periyotta otomatik DRAFT expense üretimi için kaynak.
CREATE TABLE expense_category (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_expense_category_business_name UNIQUE (business_id, name)
);
CREATE INDEX idx_expense_category_business_id ON expense_category (business_id);

CREATE TABLE recurring_expense_template (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    branch_id UUID REFERENCES branch (id),
    category_id UUID NOT NULL REFERENCES expense_category (id),
    amount_minor_units BIGINT NOT NULL,
    vendor VARCHAR(255),
    description VARCHAR(1000),
    recurrence VARCHAR(10) NOT NULL CHECK (recurrence IN ('MONTHLY')),
    day_of_month INT NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    start_date DATE NOT NULL,
    end_date DATE,
    active BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX idx_recurring_expense_template_business_id ON recurring_expense_template (business_id);

CREATE TABLE expense (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    branch_id UUID REFERENCES branch (id),
    category_id UUID NOT NULL REFERENCES expense_category (id),
    amount_minor_units BIGINT NOT NULL,
    incurred_at DATE NOT NULL,
    vendor VARCHAR(255),
    description VARCHAR(1000),
    receipt_image_url VARCHAR(2048),
    -- Null for scheduler-generated recurring drafts (Section 16.2) - no staff actor initiated those.
    created_by_staff_user_id UUID REFERENCES staff_user (id),
    status VARCHAR(10) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    approved_by_staff_user_id UUID REFERENCES staff_user (id),
    approved_at TIMESTAMPTZ,
    source_template_id UUID REFERENCES recurring_expense_template (id),
    generated_for_period VARCHAR(7),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_expense_business_id ON expense (business_id);
CREATE INDEX idx_expense_branch_id ON expense (branch_id);
-- Bir şablon aynı dönem (YYYY-MM) için iki kez otomatik gider üretmesin.
CREATE UNIQUE INDEX uq_expense_template_period ON expense (source_template_id, generated_for_period)
    WHERE source_template_id IS NOT NULL;
