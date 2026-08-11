package com.qrmenu.payment;

import java.util.UUID;

public record PaymentIntentView(UUID paymentId, UUID orderId, String status, long amountMinorUnits, String provider) {
}
