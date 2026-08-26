package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.Allergen;
import java.util.Set;
import java.util.UUID;

public record ProductAdminResponse(
        UUID id,
        UUID businessId,
        UUID categoryId,
        String name,
        String description,
        String imageUrl,
        long basePriceMinorUnits,
        int displayOrder,
        boolean active,
        Integer estimatedPreparationMinutes,
        Set<Allergen> allergens) {
}
