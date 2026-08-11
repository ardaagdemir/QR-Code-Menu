package com.qrmenu.payment.web.dto;

import java.util.UUID;

public record PaymentStatusResponse(UUID paymentId, UUID orderId, String paymentStatus, String orderStatus) {
}
