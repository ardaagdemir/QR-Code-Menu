package com.qrmenu.menu.web.dto;

import java.util.UUID;

public record MenuCategoryAdminResponse(UUID id, UUID businessId, String name, int displayOrder) {
}
