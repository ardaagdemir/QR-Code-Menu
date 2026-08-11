package com.qrmenu.refund.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundResponse(
        UUID refundId, UUID orderId, String status, long totalAmountMinorUnits, Instant createdAt, List<RefundItemResponse> items) {
}
