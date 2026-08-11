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
 * Named CustomerOrder (not Order) to avoid ambiguity with
 * org.springframework.core.annotation.Order / jakarta.persistence.criteria.Order, same
 * reasoning as RestaurantTable avoiding jakarta.persistence.Table - and the DB table is
 * "customer_order" for the same reason "restaurant_table" isn't "table" (SQL keyword).
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "table_visit_id", nullable = false)
    private UUID tableVisitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "tracking_token_hash", nullable = false)
    private String trackingTokenHash;

    @Column(name = "total_minor_units", nullable = false)
    private long totalMinorUnits;

    /** Null until PAID (Section 5: "okunabilir sipariş numarası") - see OrderNumberGenerator. */
    @Column(name = "order_number")
    private Integer orderNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected CustomerOrder() {
        // JPA
    }

    public CustomerOrder(UUID businessId, UUID branchId, UUID tableVisitId, String trackingTokenHash) {
        this.businessId = businessId;
        this.branchId = branchId;
        this.tableVisitId = tableVisitId;
        this.status = OrderStatus.DRAFT;
        this.trackingTokenHash = trackingTokenHash;
        this.totalMinorUnits = 0L;
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastActivityAt = now;
    }

    public void recalculateTotal(long totalMinorUnits) {
        this.totalMinorUnits = totalMinorUnits;
        this.lastActivityAt = Instant.now();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * DRAFT -> AWAITING_PAYMENT starts a first payment attempt; PAYMENT_FAILED ->
     * AWAITING_PAYMENT is the "yeni ödeme denemesi" retry from Section 6's Payment
     * state machine.
     */
    public void markAwaitingPayment() {
        if (status != OrderStatus.DRAFT && status != OrderStatus.PAYMENT_FAILED) {
            throw new IllegalStateException("Cannot start payment for an order in status " + status);
        }
        this.status = OrderStatus.AWAITING_PAYMENT;
        this.lastActivityAt = Instant.now();
    }

    public void markPaid() {
        if (status != OrderStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException("Cannot mark paid an order in status " + status);
        }
        this.status = OrderStatus.PAID;
        this.lastActivityAt = Instant.now();
    }

    public void markPaymentFailed() {
        if (status != OrderStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException("Cannot mark payment-failed an order in status " + status);
        }
        this.status = OrderStatus.PAYMENT_FAILED;
        this.lastActivityAt = Instant.now();
    }

    public void assignOrderNumber(int orderNumber) {
        if (this.orderNumber != null) {
            throw new IllegalStateException("Order already has a number assigned: " + this.orderNumber);
        }
        this.orderNumber = orderNumber;
    }

    /**
     * PAID -> IN_KITCHEN, "otomatik" (Section 6) - called synchronously right after
     * markPaid() in the same transaction/method (OrderingService.markOrderPaid), NOT
     * via the OrderPaid outbox event: the outbox poller's fixedDelay is 10s (Section 9,
     * Milestone 5), which would add a real, user-visible lag before an order reaches
     * the kitchen - unacceptable for a screen that's explicitly meant to be real-time
     * (SSE). The OrderPaid outbox event is still written for any future durable/
     * decoupled consumer (e.g. audit), it's just not what queues the kitchen.
     */
    public void markInKitchen() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Cannot move to IN_KITCHEN from status " + status);
        }
        this.status = OrderStatus.IN_KITCHEN;
        this.lastActivityAt = Instant.now();
    }

    /**
     * IN_KITCHEN -> READY: "kabul edilen tüm kalemler hazır" (Section 6). A fully
     * rejected order (every OrderItem REJECTED) also ends up here - Section 6's note
     * that Order-level REJECTED is a derived rollup, not a persisted status, means this
     * is the correct terminal status for that case too; a later milestone can inspect
     * the items to distinguish "ready for pickup" from "fully rejected, refund" without
     * needing a separate Order status value.
     */
    public void markReady() {
        if (status != OrderStatus.IN_KITCHEN) {
            throw new IllegalStateException("Cannot move to READY from status " + status);
        }
        this.status = OrderStatus.READY;
        this.lastActivityAt = Instant.now();
    }

    /**
     * READY -> COMPLETED: "teslim edildi / alındı" (Section 6, Milestone 9) - a manual
     * staff action (either a BRANCH_MANAGER marking a WAITER_DELIVERY order delivered,
     * or the pickup counter marking a CUSTOMER_PICKUP order collected), not an automatic
     * rollup like markReady's item-driven trigger.
     */
    public void markCompleted() {
        if (status != OrderStatus.READY) {
            throw new IllegalStateException("Cannot move to COMPLETED from status " + status);
        }
        this.status = OrderStatus.COMPLETED;
        this.lastActivityAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getTableVisitId() {
        return tableVisitId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getTrackingTokenHash() {
        return trackingTokenHash;
    }

    public long getTotalMinorUnits() {
        return totalMinorUnits;
    }

    public Integer getOrderNumber() {
        return orderNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }
}
