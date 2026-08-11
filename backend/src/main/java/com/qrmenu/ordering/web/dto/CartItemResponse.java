package com.qrmenu.ordering.web.dto;

import java.util.List;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID productId,
        String productName,
        long unitPriceMinorUnits,
        int quantity,
        long lineTotalMinorUnits,
        List<CartItemOptionResponse> options) {
}
