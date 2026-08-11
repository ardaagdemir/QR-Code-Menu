package com.qrmenu.ordering;

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
 * productId/productNameSnapshot/unitPriceMinorUnits are an immutable SNAPSHOT taken at
 * add-to-cart time, not a live reference to Product (Section 5: "Product'a canlı
 * referans değil"; a later menu edit must not retroactively change this order).
 *
 * acceptedQuantity/rejectedQuantity exist in the confirmed domain model (Section 5);
 * Milestone 6's kitchen accept/reject decision (decide()) is what actually sets them.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "unit_price_minor_units", nullable = false)
    private long unitPriceMinorUnits;

    @Column(name = "ordered_quantity", nullable = false)
    private int orderedQuantity;

    @Column(name = "accepted_quantity", nullable = false)
    private int acceptedQuantity;

    @Column(name = "rejected_quantity", nullable = false)
    private int rejectedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderItemStatus status;

    @Column(name = "line_total_minor_units", nullable = false)
    private long lineTotalMinorUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderItem() {
        // JPA
    }

    public OrderItem(
            UUID orderId,
            UUID productId,
            String productNameSnapshot,
            long unitPriceMinorUnits,
            int orderedQuantity,
            long lineTotalMinorUnits) {
        this.orderId = orderId;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.unitPriceMinorUnits = unitPriceMinorUnits;
        this.orderedQuantity = orderedQuantity;
        this.acceptedQuantity = 0;
        this.rejectedQuantity = 0;
        this.status = OrderItemStatus.PENDING_REVIEW;
        this.lineTotalMinorUnits = lineTotalMinorUnits;
        this.createdAt = Instant.now();
    }

    /**
     * PENDING_REVIEW -> PREPARING (acceptedQuantity > 0) or REJECTED (acceptedQuantity
     * == 0, tam red) - the kitchen's one-time decision (Section 6 invariant:
     * "acceptedQuantity + rejectedQuantity = orderedQuantity ... karar anında set
     * edilir, sonradan değişmez").
     */
    public void decide(int acceptedQuantity) {
        if (status != OrderItemStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Cannot decide an order item in status " + status);
        }
        if (acceptedQuantity < 0 || acceptedQuantity > orderedQuantity) {
            throw new IllegalArgumentException(
                    "acceptedQuantity must be between 0 and " + orderedQuantity + ": " + acceptedQuantity);
        }
        this.acceptedQuantity = acceptedQuantity;
        this.rejectedQuantity = orderedQuantity - acceptedQuantity;
        this.status = acceptedQuantity == 0 ? OrderItemStatus.REJECTED : OrderItemStatus.PREPARING;
    }

    public void markReady() {
        if (status != OrderItemStatus.PREPARING) {
            throw new IllegalStateException("Cannot mark ready an order item in status " + status);
        }
        this.status = OrderItemStatus.READY;
    }

    public void markServed() {
        if (status != OrderItemStatus.READY) {
            throw new IllegalStateException("Cannot mark served an order item in status " + status);
        }
        this.status = OrderItemStatus.SERVED;
    }

    public OrderItemStatus getStatus() {
        return status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public long getUnitPriceMinorUnits() {
        return unitPriceMinorUnits;
    }

    public int getOrderedQuantity() {
        return orderedQuantity;
    }

    public int getAcceptedQuantity() {
        return acceptedQuantity;
    }

    public int getRejectedQuantity() {
        return rejectedQuantity;
    }

    public long getLineTotalMinorUnits() {
        return lineTotalMinorUnits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
