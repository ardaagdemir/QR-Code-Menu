package com.qrmenu.tenant.web.dto;

import java.time.LocalTime;
import java.util.UUID;

public record BranchResponse(
        UUID id,
        UUID businessId,
        String name,
        boolean orderingEnabled,
        LocalTime openingTime,
        LocalTime closingTime,
        String deliveryModel) {
}
