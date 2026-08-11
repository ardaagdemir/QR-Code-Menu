package com.qrmenu.payment;

import java.util.UUID;

public record PaymentStatusView(UUID paymentId, UUID orderId, String paymentStatus, String orderStatus) {
}
