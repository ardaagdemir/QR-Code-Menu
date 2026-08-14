-- Pre-existing bug: payment_status_check (V7) never included EXPIRED even though
-- Payment.markExpired() (PROCESSING -> EXPIRED, PaymentTimeoutScheduler's "webhook never
-- arrived" path) has always set exactly that value - every real expiry attempt has been
-- silently failing the DB write in production. Surfaced by making the scheduler's tests
-- exercise a real commit instead of relying on the same Hibernate session's in-memory
-- entity state.

ALTER TABLE payment DROP CONSTRAINT payment_status_check;
ALTER TABLE payment ADD CONSTRAINT payment_status_check
    CHECK (status IN ('CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'EXPIRED'));
