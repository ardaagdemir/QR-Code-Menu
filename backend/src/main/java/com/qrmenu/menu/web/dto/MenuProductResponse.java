package com.qrmenu.menu.web.dto;

import java.util.List;
import java.util.UUID;

public record MenuProductResponse(
        UUID id,
        String name,
        String description,
        String imageUrl,
        long priceMinorUnits,
        int taxRatePercent,
        String availability,
        List<MenuOptionGroupResponse> optionGroups) {
}
