-- Keep generated expense history linked to its source template while allowing the
-- template to be removed from management and all future scheduler runs.
ALTER TABLE recurring_expense_template
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_recurring_expense_template_scheduler
    ON recurring_expense_template (active, deleted)
    WHERE active = TRUE AND deleted = FALSE;
