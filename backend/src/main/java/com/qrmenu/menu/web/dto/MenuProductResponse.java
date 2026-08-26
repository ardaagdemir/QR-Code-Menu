package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.Allergen;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MenuProductResponse(
        UUID id,
        String name,
        String description,
        String imageUrl,
        long priceMinorUnits,
        String availability,
        Integer estimatedPreparationMinutes,
        Set<Allergen> allergens,
        List<MenuOptionGroupResponse> optionGroups) {
}
