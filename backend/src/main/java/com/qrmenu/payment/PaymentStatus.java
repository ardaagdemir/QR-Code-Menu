package com.qrmenu.payment;

/**
 * Milestone 5 added CREATED/PROCESSING/SUCCEEDED/FAILED. Milestone 9 adds EXPIRED
 * (Section 6, Section 9: "hata senaryoları - webhook timeout" - PaymentTimeoutScheduler
 * expires a PROCESSING payment that never received a webhook). CANCELLED (Section 6:
 * "kullanıcı vazgeçti") still has no code path - there's no "cancel my in-progress
 * payment" UI action anywhere in the product - so it's deliberately still not added,
 * same reasoning as before.
 */
public enum PaymentStatus {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    EXPIRED
}
