package com.qrmenu.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt at paying for an Order (Section 5: "Order 1-* Payment ... en fazla 1
 * tanesi SUCCEEDED olabilir" - enforced by a partial unique index in
 * V7__payment_webhook_and_outbox.sql, not in this entity). businessId/orderId are
 * plain columns, not JPA associations - same "no magic, every id visible in code"
 * convention as CustomerOrder/Branch.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_payment_intent_id")
    private String providerPaymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Column(name = "total_refunded_amount_minor_units", nullable = false)
    private long totalRefundedAmountMinorUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // JPA
    }

    public Payment(UUID businessId, UUID orderId, String provider, long amountMinorUnits) {
        this.businessId = businessId;
        this.orderId = orderId;
        this.provider = provider;
        this.amountMinorUnits = amountMinorUnits;
        this.status = PaymentStatus.CREATED;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** CREATED -> PROCESSING: the provider now hosts the intent (Section 6, "sağlayıcıya yönlendirildi"). */
    public void markProcessing(String providerPaymentIntentId) {
        if (status != PaymentStatus.CREATED) {
            throw new IllegalStateException("Cannot move to PROCESSING from status " + status);
        }
        this.providerPaymentIntentId = providerPaymentIntentId;
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markSucceeded() {
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot move to SUCCEEDED from status " + status);
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot move to FAILED from status " + status);
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    /** PROCESSING -> EXPIRED (Section 6, Milestone 9): no webhook ever arrived within the timeout window. */
    public void markExpired() {
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot move to EXPIRED from status " + status);
        }
        this.status = PaymentStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    /**
     * Section 1.3 risk: "Toplam iade tutarı aşımı ... Payment.totalRefundedAmount aynı
     * transaction'da güncellenir" - the one hard invariant Milestone 7 must enforce.
     * Only a SUCCEEDED payment can be refunded, and the running total can never exceed
     * what was actually paid.
     */
    public void applyRefund(long refundAmountMinorUnits) {
        if (status != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Cannot refund a payment in status " + status);
        }
        if (refundAmountMinorUnits <= 0) {
            throw new IllegalArgumentException("refundAmountMinorUnits must be positive: " + refundAmountMinorUnits);
        }
        long newTotal = totalRefundedAmountMinorUnits + refundAmountMinorUnits;
        if (newTotal > amountMinorUnits) {
            throw new IllegalStateException(
                    "Refund would exceed the paid amount: requested total " + newTotal + " > paid " + amountMinorUnits);
        }
        this.totalRefundedAmountMinorUnits = newTotal;
        this.updatedAt = Instant.now();
    }

    /** Releases an in-transaction refund reservation when the provider rejects it. */
    public void releaseRefund(long refundAmountMinorUnits) {
        if (refundAmountMinorUnits <= 0 || refundAmountMinorUnits > totalRefundedAmountMinorUnits) {
            throw new IllegalArgumentException("Invalid refund release amount: " + refundAmountMinorUnits);
        }
        this.totalRefundedAmountMinorUnits -= refundAmountMinorUnits;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderPaymentIntentId() {
        return providerPaymentIntentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }

    public long getTotalRefundedAmountMinorUnits() {
        return totalRefundedAmountMinorUnits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
