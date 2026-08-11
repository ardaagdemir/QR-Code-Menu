-- Gap-analysis #9 (product-requirements.md Section 14): gün sonu kapanış snapshot'ı.
-- Bir branch+businessDate için tek satır - PREVIEW status güncellenebilir, FINAL immutable
-- (uygulama katmanında zorlanıyor - burada sadece unique constraint var).
CREATE TABLE daily_branch_close_report (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    business_date DATE NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    gross_sales_minor_units BIGINT NOT NULL,
    refund_total_minor_units BIGINT NOT NULL,
    net_sales_minor_units BIGINT NOT NULL,
    order_count INT NOT NULL,
    accepted_order_count INT NOT NULL,
    rejected_order_count INT NOT NULL,
    average_order_value_minor_units BIGINT NOT NULL,
    table_visit_count BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (branch_id, business_date)
);
CREATE INDEX idx_daily_branch_close_report_business_id ON daily_branch_close_report (business_id);
