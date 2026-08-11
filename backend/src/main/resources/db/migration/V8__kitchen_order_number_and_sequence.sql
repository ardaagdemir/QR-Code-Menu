-- Milestone 6: Kitchen Display, OrderItem kısmi adet kabul/red, sipariş numarası.
-- customer_order gains IN_KITCHEN/READY (Section 6) + order_number (Section 5:
-- "okunabilir sipariş numarası", per-branch/per-day, assigned when PAID).

ALTER TABLE customer_order DROP CONSTRAINT customer_order_status_check;
ALTER TABLE customer_order ADD CONSTRAINT customer_order_status_check
    CHECK (status IN ('DRAFT', 'CANCELLED', 'AWAITING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'IN_KITCHEN', 'READY'));

ALTER TABLE customer_order ADD COLUMN order_number INT;

-- OrderItem's kitchen decision state (Section 6): PENDING_REVIEW (default, until the
-- kitchen decides) -> PREPARING|REJECTED -> READY -> SERVED. Distinct from the
-- acceptedQuantity/rejectedQuantity columns (Milestone 4) which hold the quantity
-- split the decision produces.
ALTER TABLE order_item ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'
    CHECK (status IN ('PENDING_REVIEW', 'PREPARING', 'REJECTED', 'READY', 'SERVED'));

-- Per-branch, per-day readable order number counter (Section 1.3 risk: "Sipariş
-- numarası çakışması ... DB sequence veya SELECT ... FOR UPDATE ile şube bazlı sayaç
-- güvenceye alınmalı"). Incremented via a single atomic
-- INSERT ... ON CONFLICT DO UPDATE ... RETURNING (OrderNumberGenerator), which avoids
-- the read-then-write race a SELECT+INSERT/UPDATE pair would have.
CREATE TABLE branch_daily_order_sequence (
    branch_id UUID NOT NULL REFERENCES branch (id),
    order_date DATE NOT NULL,
    last_number INT NOT NULL DEFAULT 0,
    PRIMARY KEY (branch_id, order_date)
);
