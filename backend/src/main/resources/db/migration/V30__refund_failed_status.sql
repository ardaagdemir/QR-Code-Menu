ALTER TABLE refund DROP CONSTRAINT refund_status_check;

ALTER TABLE refund
    ADD CONSTRAINT refund_status_check
    CHECK (status IN ('REQUESTED', 'PROCESSING', 'COMPLETED', 'FAILED'));
