package com.qrmenu.menu.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOptionRequest(@NotBlank String name, Long priceDeltaMinorUnits, Integer displayOrder) {

    public long priceDeltaOrDefault() {
        return priceDeltaMinorUnits == null ? 0L : priceDeltaMinorUnits;
    }

    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
