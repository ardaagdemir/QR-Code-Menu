package com.qrmenu.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundView(
        UUID refundId, UUID orderId, String status, long totalAmountMinorUnits, Instant createdAt, List<RefundItemView> items) {
}
