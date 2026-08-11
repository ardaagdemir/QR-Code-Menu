package com.qrmenu.payment;

import java.util.UUID;

/** What the refund module needs to know about an order's succeeded payment - never exposes the full entity. */
public record PaymentSummaryView(
        UUID paymentId,
        UUID businessId,
        String providerPaymentIntentId,
        long amountMinorUnits,
        long totalRefundedAmountMinorUnits,
        String status) {

    public long remainingRefundableMinorUnits() {
        return amountMinorUnits - totalRefundedAmountMinorUnits;
    }
}
