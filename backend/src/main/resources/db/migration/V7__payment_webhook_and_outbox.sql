-- Milestone 5: PaymentProviderPort + MockPaymentProviderAdapter, webhook idempotency,
-- Transactional Outbox. customer_order gains AWAITING_PAYMENT/PAID/PAYMENT_FAILED
-- (Section 6) - the constraint name below is Postgres's default
-- "<table>_<column>_check" naming for the single unnamed CHECK defined in V5.

ALTER TABLE customer_order DROP CONSTRAINT customer_order_status_check;
ALTER TABLE customer_order ADD CONSTRAINT customer_order_status_check
    CHECK (status IN ('DRAFT', 'CANCELLED', 'AWAITING_PAYMENT', 'PAID', 'PAYMENT_FAILED'));

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    order_id UUID NOT NULL REFERENCES customer_order (id),
    provider VARCHAR(30) NOT NULL,
    -- Set once the provider hosts the intent (CREATED -> PROCESSING); null while CREATED.
    provider_payment_intent_id VARCHAR(255),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    amount_minor_units BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_business_id ON payment (business_id);
CREATE INDEX idx_payment_order_id ON payment (order_id);
CREATE INDEX idx_payment_provider_intent ON payment (provider, provider_payment_intent_id);
-- "Aynı anda tek başarılı ödeme garantisi" (Section 1.3): at most one SUCCEEDED payment
-- per order, enforced at the DB level via a partial unique index.
CREATE UNIQUE INDEX uq_payment_one_succeeded_per_order ON payment (order_id) WHERE status = 'SUCCEEDED';

-- Webhook idempotency (Section 1.3): "(provider, event_id) üzerinde DB unique
-- constraint" - a duplicate delivery of the same provider event is rejected by this
-- constraint, not by a racy read-then-write check.
CREATE TABLE payment_webhook_event (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    payment_id UUID REFERENCES payment (id),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_webhook_event_provider_event_id UNIQUE (provider, event_id)
);

-- Transactional Outbox (Section 3: "teknik, domain'e ait değil"; Section 12: no
-- Kafka/message queue for v1 - a single-instance @Scheduled poller is enough).
-- payload is the JSON-serialized event body; published_at is null until the poller
-- has dispatched it to in-process subscribers.
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
