package com.qrmenu.refund;

/**
 * Only the states this milestone's mock provider path can actually reach (Section 6
 * has FAILED too, for a provider that can reject a refund request - the mock never
 * does, so it's not added here, same "no unreachable enum values" discipline as
 * PaymentStatus/OrderItemStatus).
 */
public enum RefundStatus {
    REQUESTED,
    PROCESSING,
    COMPLETED
}
