package com.qrmenu.tenant.web.dto;

import java.util.UUID;

public record TableResponse(UUID id, UUID businessId, UUID branchId, String label, boolean active) {
}
