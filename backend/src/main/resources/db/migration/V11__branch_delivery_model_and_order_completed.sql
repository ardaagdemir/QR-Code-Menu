-- Milestone 9: CUSTOMER_PICKUP/WAITER_DELIVERY branch-level delivery model (Section 4,
-- staff-web screen #8 "Pickup board"; Section 9, Milestone 9). Defaults to
-- WAITER_DELIVERY - the model every existing branch (Milestone 1-8) implicitly used,
-- so this migration is additive and doesn't change behaviour for branches created
-- before this column existed.
ALTER TABLE branch
    ADD COLUMN delivery_model VARCHAR(20) NOT NULL DEFAULT 'WAITER_DELIVERY'
        CHECK (delivery_model IN ('CUSTOMER_PICKUP', 'WAITER_DELIVERY'));

-- Milestone 9: the READY -> COMPLETED transition from product-requirements.md Section 6
-- ("teslim edildi / alındı") was never implemented before this milestone - customer_order
-- rows could only reach READY. Widen V8's status CHECK constraint to allow it.
ALTER TABLE customer_order DROP CONSTRAINT customer_order_status_check;
ALTER TABLE customer_order ADD CONSTRAINT customer_order_status_check
    CHECK (status IN ('DRAFT', 'CANCELLED', 'AWAITING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'IN_KITCHEN', 'READY', 'COMPLETED'));
