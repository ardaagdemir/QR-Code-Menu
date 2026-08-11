package com.qrmenu.menu.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record CreateProductRequest(
        @NotNull UUID categoryId,
        @NotBlank String name,
        String description,
        String imageUrl,
        @PositiveOrZero long basePriceMinorUnits,
        @NotNull @PositiveOrZero Integer taxRatePercent,
        Integer displayOrder) {

    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
