package com.qrmenu.reporting.web.dto;

import java.util.UUID;

public record CategorySalesResponse(UUID categoryId, String categoryName, long revenueMinorUnits) {
}
