-- An expense is effective as soon as it is persisted. Approval state is no longer
-- part of the product model; all existing rows are preserved as recorded expenses.
-- Recurring history remains identifiable and immutable through source_template_id
-- and generated_for_period.
ALTER TABLE expense
    DROP COLUMN status,
    DROP COLUMN approved_by_staff_user_id,
    DROP COLUMN approved_at;
