package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.Allergen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;
import java.util.UUID;

public record CreateProductRequest(
        @NotNull UUID categoryId,
        @NotBlank String name,
        String description,
        String imageUrl,
        @PositiveOrZero long basePriceMinorUnits,
        @NotNull @PositiveOrZero Integer taxRatePercent,
        Integer displayOrder,
        Boolean active,
        @PositiveOrZero Integer estimatedPreparationMinutes,
        Set<Allergen> allergens) {

    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }

    public Set<Allergen> allergensOrEmpty() {
        return allergens == null ? Set.of() : allergens;
    }
}
