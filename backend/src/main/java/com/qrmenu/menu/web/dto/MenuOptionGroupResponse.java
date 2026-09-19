package com.qrmenu.menu.web.dto;

import java.util.List;
import java.util.UUID;

public record MenuOptionGroupResponse(
        UUID id, String name, String selectionType, boolean required, List<MenuOptionResponse> options) {
}
