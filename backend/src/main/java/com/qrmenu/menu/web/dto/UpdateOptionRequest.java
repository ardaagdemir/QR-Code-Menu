package com.qrmenu.menu.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateOptionRequest(@NotBlank String name, Long priceDeltaMinorUnits) {

    public long priceDeltaOrDefault() {
        return priceDeltaMinorUnits == null ? 0L : priceDeltaMinorUnits;
    }
}
