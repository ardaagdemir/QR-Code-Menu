package com.qrmenu.ordering.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderControlOrderResponse(
        UUID orderId,
        Integer orderNumber,
        String status,
        long totalMinorUnits,
        String rejectionReasonCode,
        String rejectionNote,
        String tableLabel,
        Instant statusSince,
        List<OrderControlOrderItemResponse> items) {
}
