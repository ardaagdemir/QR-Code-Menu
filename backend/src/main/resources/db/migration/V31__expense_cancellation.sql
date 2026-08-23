-- Manual expenses can be voided without a hard delete so the record stays auditable.
-- cancelled_at IS NOT NULL is the cancellation flag; cancelled rows stay visible in the
-- list but are excluded from expense report totals (see ExpenseRepository sum queries).
ALTER TABLE expense
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by_staff_user_id UUID;
