package com.qrmenu.ordering;

/**
 * Gap-analysis #1 (kasa kabul/red kapısı): PAID removed - a verified payment webhook now
 * lands the order in AWAITING_STORE_ACCEPTANCE, not directly in the kitchen queue.
 * IN_KITCHEN is only reachable through an explicit cashier ACCEPT
 * (OrderingService.acceptOrder); REJECTED_BY_STORE is the terminal state for a cashier
 * REJECT, which also triggers a full refund (RefundService.requestFullRefund) - see
 * product-requirements.md Section 6/7. READY/COMPLETED unchanged from Milestone 6/9. A
 * persisted item-level REJECTED value is deliberately NOT added at the Order level, see
 * CustomerOrder Javadoc.
 */
public enum OrderStatus {
    DRAFT,
    CANCELLED,
    AWAITING_PAYMENT,
    PAYMENT_FAILED,
    AWAITING_STORE_ACCEPTANCE,
    REJECTED_BY_STORE,
    IN_KITCHEN,
    READY,
    COMPLETED
}
