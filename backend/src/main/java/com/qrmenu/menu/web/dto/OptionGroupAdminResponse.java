package com.qrmenu.menu.web.dto;

import java.util.UUID;

public record OptionGroupAdminResponse(UUID id, UUID businessId, UUID productId, String name, String selectionType, int displayOrder) {
}
