package com.qrmenu.refund;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Section 5: "RefundItem: orderItemId, rejectedQuantity, refundAmount - bir OrderItem'ın
 * reddedilen kısmını belirli bir Refund'a bağlar." orderItemId has no FK (same
 * immutable-snapshot convention as order_item not referencing product) and no back-
 * reference to OrderItem's own status - a RefundItem is a standalone historical record
 * of what was refunded, for what quantity, and for how much.
 */
@Entity
@Table(name = "refund_item")
public class RefundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "refund_id", nullable = false)
    private UUID refundId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "refunded_quantity", nullable = false)
    private int refundedQuantity;

    @Column(name = "refund_amount_minor_units", nullable = false)
    private long refundAmountMinorUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefundItem() {
        // JPA
    }

    public RefundItem(UUID refundId, UUID orderItemId, int refundedQuantity, long refundAmountMinorUnits) {
        this.refundId = refundId;
        this.orderItemId = orderItemId;
        this.refundedQuantity = refundedQuantity;
        this.refundAmountMinorUnits = refundAmountMinorUnits;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public int getRefundedQuantity() {
        return refundedQuantity;
    }

    public long getRefundAmountMinorUnits() {
        return refundAmountMinorUnits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
