-- Staff lifecycle management (business-scoped email edit) now normalizes email to
-- trim+lowercase at the application layer before every write, but the original
-- uq_staff_user_email constraint is case-sensitive - a DB-level guarantee is needed so
-- two concurrent requests can never race past the app-layer uniqueness check into two
-- rows that only differ by case. Existing rows are normalized first so the new index
-- can be created without a pre-existing collision.
UPDATE staff_user SET email = LOWER(TRIM(email)) WHERE email <> LOWER(TRIM(email));

ALTER TABLE staff_user DROP CONSTRAINT uq_staff_user_email;

CREATE UNIQUE INDEX uq_staff_user_email_lower ON staff_user (LOWER(email));
