package com.qrmenu.refund.web.dto;

import java.util.UUID;

public record StaffOrderItemResponse(
        UUID id,
        String productName,
        int orderedQuantity,
        int acceptedQuantity,
        int rejectedQuantity,
        String status,
        long unitPriceMinorUnits,
        long lineTotalMinorUnits) {
}
