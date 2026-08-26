-- Expense category rename/reactivate lifecycle now normalizes name to trim() at the
-- application layer before every write, but the original uq_expense_category_business_name
-- constraint is case-sensitive - a DB-level guarantee is needed so two concurrent requests
-- can never race past the app-layer uniqueness check into two rows that only differ by
-- case (mirrors V38's staff_user email approach). Existing rows are trimmed first so the
-- new index can be created without a pre-existing collision.
UPDATE expense_category SET name = TRIM(name) WHERE name <> TRIM(name);

ALTER TABLE expense_category DROP CONSTRAINT uq_expense_category_business_name;

CREATE UNIQUE INDEX uq_expense_category_business_name_lower ON expense_category (business_id, LOWER(name));
