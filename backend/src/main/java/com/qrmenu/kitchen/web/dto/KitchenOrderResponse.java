package com.qrmenu.kitchen.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenOrderResponse(
        UUID orderId,
        Integer orderNumber,
        String status,
        long totalMinorUnits,
        String tableLabel,
        Instant statusSince,
        List<KitchenOrderItemResponse> items) {
}
