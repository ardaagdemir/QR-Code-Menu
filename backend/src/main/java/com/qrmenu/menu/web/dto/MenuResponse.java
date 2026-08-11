package com.qrmenu.menu.web.dto;

import java.util.List;
import java.util.UUID;

public record MenuResponse(UUID branchId, List<MenuCategoryResponse> categories) {
}
