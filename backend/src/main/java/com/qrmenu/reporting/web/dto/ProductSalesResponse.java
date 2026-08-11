package com.qrmenu.reporting.web.dto;

import java.util.UUID;

public record ProductSalesResponse(UUID productId, String productName, int quantitySold, long revenueMinorUnits) {
}
