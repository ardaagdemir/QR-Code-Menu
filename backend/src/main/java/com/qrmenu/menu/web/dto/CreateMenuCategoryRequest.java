package com.qrmenu.menu.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMenuCategoryRequest(@NotBlank String name, Integer displayOrder) {

    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
