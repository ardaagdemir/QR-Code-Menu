package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.Allergen;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;

public record UpdateProductDetailsRequest(
        @NotNull Boolean active, @PositiveOrZero Integer estimatedPreparationMinutes, Set<Allergen> allergens) {

    public Set<Allergen> allergensOrEmpty() {
        return allergens == null ? Set.of() : allergens;
    }
}
