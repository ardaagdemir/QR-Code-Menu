package com.qrmenu.payment.web.dto;

import java.util.UUID;

public record PaymentIntentResponse(UUID paymentId, UUID orderId, String status, long amountMinorUnits, String provider) {
}
