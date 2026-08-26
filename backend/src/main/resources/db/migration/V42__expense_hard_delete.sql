-- Manual expenses are now hard-deleted (see StaffExpenseController DELETE endpoint) instead
-- of soft-voided, matching the "Sil" pattern already used for recurring templates and business
-- contacts. The soft-void columns from V31 are no longer written or read.
ALTER TABLE expense
    DROP COLUMN cancelled_at,
    DROP COLUMN cancelled_by_staff_user_id;
