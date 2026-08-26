-- Platform admin panel: PLATFORM_ADMIN-only hard delete of a StaffUser. The FKs a
-- deleted staff_user row is referenced by must not block the DELETE - ON DELETE SET
-- NULL lets history (audit_log_entry/expense/owner_notification_log) survive the
-- delete instead. actor_account_deleted distinguishes "actor became null because the
-- account was hard-deleted" from the pre-existing "actor was null because there was
-- never a human actor" case (e.g. /internal bootstrap), which audit_log_entry could not
-- previously tell apart.
ALTER TABLE audit_log_entry
    ADD COLUMN actor_account_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE audit_log_entry
    DROP CONSTRAINT audit_log_entry_actor_staff_user_id_fkey,
    ADD CONSTRAINT audit_log_entry_actor_staff_user_id_fkey
        FOREIGN KEY (actor_staff_user_id) REFERENCES staff_user (id) ON DELETE SET NULL;

ALTER TABLE expense
    DROP CONSTRAINT expense_created_by_staff_user_id_fkey,
    ADD CONSTRAINT expense_created_by_staff_user_id_fkey
        FOREIGN KEY (created_by_staff_user_id) REFERENCES staff_user (id) ON DELETE SET NULL;

-- cancelled_by_staff_user_id (V31) was never given an FK at all - added fresh here.
ALTER TABLE expense
    ADD CONSTRAINT expense_cancelled_by_staff_user_id_fkey
        FOREIGN KEY (cancelled_by_staff_user_id) REFERENCES staff_user (id) ON DELETE SET NULL;

ALTER TABLE owner_notification_log
    DROP CONSTRAINT owner_notification_log_triggered_by_staff_user_id_fkey,
    ADD CONSTRAINT owner_notification_log_triggered_by_staff_user_id_fkey
        FOREIGN KEY (triggered_by_staff_user_id) REFERENCES staff_user (id) ON DELETE SET NULL;

-- staff_user_branch and staff_session are deliberately left without cascade here - the
-- service layer clears them explicitly (StaffAuthService.hardDeleteStaffUserAsPlatformAdmin)
-- before the delete, rather than relying on a DB-level cascade.
