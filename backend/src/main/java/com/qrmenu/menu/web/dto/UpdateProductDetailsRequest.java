package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.Allergen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;

public record UpdateProductDetailsRequest(
        @NotBlank String name,
        String description,
        @PositiveOrZero long basePriceMinorUnits,
        @NotNull @PositiveOrZero Integer taxRatePercent,
        @NotNull Boolean active,
        @PositiveOrZero Integer estimatedPreparationMinutes,
        Set<Allergen> allergens,
        String imageUrl) {

    public Set<Allergen> allergensOrEmpty() {
        return allergens == null ? Set.of() : allergens;
    }
}
