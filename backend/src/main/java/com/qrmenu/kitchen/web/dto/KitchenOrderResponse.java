package com.qrmenu.kitchen.web.dto;

import java.util.List;
import java.util.UUID;

public record KitchenOrderResponse(
        UUID orderId, Integer orderNumber, String status, long totalMinorUnits, List<KitchenOrderItemResponse> items) {
}
