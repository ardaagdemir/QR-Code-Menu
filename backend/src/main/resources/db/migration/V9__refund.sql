-- Milestone 7: Tam/kısmi iade. payment gains a running total-refunded tracker (Section
-- 1.3 risk: "Toplam iade tutarı aşımı ... Payment.totalRefundedAmount aynı transaction'da
-- güncellenir"); refund/refund_item mirror the confirmed domain model (Section 5).

ALTER TABLE payment ADD COLUMN total_refunded_amount_minor_units BIGINT NOT NULL DEFAULT 0;

CREATE TABLE refund (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    order_id UUID NOT NULL REFERENCES customer_order (id),
    payment_id UUID NOT NULL REFERENCES payment (id),
    -- Only the states this milestone's mock provider path can actually reach - no
    -- FAILED, the mock refund call never fails and there is no retry code path.
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'PROCESSING', 'COMPLETED')),
    total_amount_minor_units BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refund_business_id ON refund (business_id);
CREATE INDEX idx_refund_order_id ON refund (order_id);
CREATE INDEX idx_refund_payment_id ON refund (payment_id);

-- No FK to order_item: same immutable-snapshot reasoning as order_item itself not
-- referencing product - a RefundItem must remain a valid historical record even if
-- somehow the order item row it describes were ever removed (it never is today, but
-- the convention is kept consistent).
CREATE TABLE refund_item (
    id UUID PRIMARY KEY,
    refund_id UUID NOT NULL REFERENCES refund (id),
    order_item_id UUID NOT NULL,
    refunded_quantity INT NOT NULL,
    refund_amount_minor_units BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refund_item_refund_id ON refund_item (refund_id);
