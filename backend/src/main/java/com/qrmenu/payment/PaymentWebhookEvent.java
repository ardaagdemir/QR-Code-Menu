package com.qrmenu.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency record for an incoming webhook delivery (Section 1.3: "(provider,
 * event_id) üzerinde DB unique constraint" is the actual dedup mechanism - this row's
 * insert either succeeds once or throws a DataIntegrityViolationException on a
 * duplicate delivery, which PaymentWebhookService treats as a no-op).
 */
@Entity
@Table(name = "payment_webhook_event")
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected PaymentWebhookEvent() {
        // JPA
    }

    public PaymentWebhookEvent(String provider, String eventId, UUID paymentId) {
        this.provider = provider;
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.receivedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getEventId() {
        return eventId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
