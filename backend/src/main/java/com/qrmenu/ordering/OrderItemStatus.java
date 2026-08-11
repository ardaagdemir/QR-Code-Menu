package com.qrmenu.ordering;

/**
 * Milestone 6 (Section 6): the kitchen's per-item decision/preparation state,
 * distinct from orderedQuantity/acceptedQuantity/rejectedQuantity (Milestone 4) which
 * hold the quantity split a PENDING_REVIEW -> PREPARING|REJECTED decision produces.
 */
public enum OrderItemStatus {
    PENDING_REVIEW,
    PREPARING,
    REJECTED,
    READY,
    SERVED
}
