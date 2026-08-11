package com.qrmenu.kitchen.web.dto;

import java.util.List;
import java.util.UUID;

public record KitchenOrderItemResponse(
        UUID id,
        String productName,
        int orderedQuantity,
        int acceptedQuantity,
        int rejectedQuantity,
        String status,
        List<KitchenOrderItemOptionResponse> options) {
}
