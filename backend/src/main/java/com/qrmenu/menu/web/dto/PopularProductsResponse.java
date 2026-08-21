package com.qrmenu.menu.web.dto;

import java.util.List;
import java.util.UUID;

public record PopularProductsResponse(UUID branchId, List<UUID> productIds) {
}
