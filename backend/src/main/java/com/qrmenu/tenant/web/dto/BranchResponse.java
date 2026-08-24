package com.qrmenu.tenant.web.dto;

import java.util.UUID;

public record BranchResponse(
        UUID id,
        UUID businessId,
        String name,
        boolean active,
        boolean orderingEnabled,
        boolean openNow,
        String address,
        String timezone,
        String deliveryModel,
        int storeAcceptanceTimeoutSeconds) {
}
