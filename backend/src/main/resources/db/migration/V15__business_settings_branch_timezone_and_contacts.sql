-- Gap-analysis #6 (product-requirements.md Section 12.1/12.2/12.3): business-level
-- default currency/timezone fallback, per-branch timezone override, and
-- BusinessContact (report recipients).
ALTER TABLE business ADD COLUMN default_currency VARCHAR(3) NOT NULL DEFAULT 'TRY';
ALTER TABLE business ADD COLUMN default_time_zone VARCHAR(50) NOT NULL DEFAULT 'Europe/Istanbul';

ALTER TABLE branch ADD COLUMN timezone VARCHAR(50);

CREATE TABLE business_contact (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(200),
    whatsapp_enabled BOOLEAN NOT NULL DEFAULT false,
    daily_report_recipient BOOLEAN NOT NULL DEFAULT false,
    monthly_report_recipient BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_business_contact_business_id ON business_contact (business_id);
