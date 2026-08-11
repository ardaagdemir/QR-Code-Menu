package com.qrmenu.menu.web.dto;

import java.util.UUID;

public record ProductAdminResponse(
        UUID id,
        UUID businessId,
        UUID categoryId,
        String name,
        String description,
        String imageUrl,
        long basePriceMinorUnits,
        int taxRatePercent,
        int displayOrder) {
}
