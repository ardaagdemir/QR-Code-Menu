package com.qrmenu.ordering.web.dto;

import java.util.List;
import java.util.UUID;

public record OrderControlOrderItemResponse(
        UUID id,
        String productName,
        int orderedQuantity,
        int acceptedQuantity,
        int rejectedQuantity,
        String status,
        List<OrderControlOrderItemOptionResponse> options) {
}
