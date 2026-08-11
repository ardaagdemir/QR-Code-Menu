package com.qrmenu.ordering.web.dto;

import java.util.List;
import java.util.UUID;

public record OrderControlOrderResponse(
        UUID orderId,
        Integer orderNumber,
        String status,
        long totalMinorUnits,
        String rejectionReasonCode,
        String rejectionNote,
        List<OrderControlOrderItemResponse> items) {
}
