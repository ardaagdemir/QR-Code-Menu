package com.qrmenu.ordering;

/**
 * Milestone 6 adds IN_KITCHEN/READY; Milestone 9 adds COMPLETED (Section 6: "READY ->
 * COMPLETED: teslim edildi / alındı" - a manual staff action, not an automatic rollup
 * like IN_KITCHEN -> READY is). A persisted REJECTED value is deliberately NOT added,
 * see CustomerOrder Javadoc.
 */
public enum OrderStatus {
    DRAFT,
    CANCELLED,
    AWAITING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    IN_KITCHEN,
    READY,
    COMPLETED
}
