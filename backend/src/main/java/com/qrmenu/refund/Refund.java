package com.qrmenu.refund;

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
 * Section 5: "Bir Order için birden fazla kısmi Refund oluşabilir" - one Refund groups
 * the RefundItem line(s) issued together in a single staff action. businessId/orderId/
 * paymentId are plain columns, not JPA associations (same "every id visible in code"
 * convention as Payment/CustomerOrder).
 */
@Entity
@Table(name = "refund")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "total_amount_minor_units", nullable = false)
    private long totalAmountMinorUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Refund() {
        // JPA
    }

    public Refund(UUID businessId, UUID orderId, UUID paymentId, long totalAmountMinorUnits) {
        this.businessId = businessId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.totalAmountMinorUnits = totalAmountMinorUnits;
        this.status = RefundStatus.REQUESTED;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markProcessing() {
        if (status != RefundStatus.REQUESTED) {
            throw new IllegalStateException("Cannot move to PROCESSING from status " + status);
        }
        this.status = RefundStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        if (status != RefundStatus.PROCESSING) {
            throw new IllegalStateException("Cannot move to COMPLETED from status " + status);
        }
        this.status = RefundStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        if (status != RefundStatus.PROCESSING) {
            throw new IllegalStateException("Cannot move to FAILED from status " + status);
        }
        this.status = RefundStatus.FAILED;
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

    public UUID getPaymentId() {
        return paymentId;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public long getTotalAmountMinorUnits() {
        return totalAmountMinorUnits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
