package com.qrmenu.menu.web.dto;

import java.util.UUID;

public record BranchProductAdminResponse(
        UUID id, UUID businessId, UUID branchId, UUID productId, String availability, Long priceOverrideMinorUnits) {
}
