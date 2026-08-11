package com.qrmenu.tenant.web.dto;

import java.util.UUID;

public record BranchResponse(
        UUID id, UUID businessId, String name, boolean orderingEnabled, String address, String timezone, String deliveryModel) {
}
