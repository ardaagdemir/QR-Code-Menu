package com.qrmenu.menu.web.dto;

import java.util.UUID;

public record OptionAdminResponse(
        UUID id, UUID businessId, UUID optionGroupId, String name, long priceDeltaMinorUnits, int displayOrder) {
}
