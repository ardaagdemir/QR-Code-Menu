package com.qrmenu.ordering.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Siparişler (order history) screen row: a completed/rejected order plus its latest refund status, if any. */
public record OrderHistoryResponse(
        UUID orderId,
        Integer orderNumber,
        String status,
        long totalMinorUnits,
        String rejectionReasonCode,
        String rejectionNote,
        String tableLabel,
        Instant createdAt,
        Instant completedAt,
        String latestRefundStatus,
        List<OrderControlOrderItemResponse> items) {
}
