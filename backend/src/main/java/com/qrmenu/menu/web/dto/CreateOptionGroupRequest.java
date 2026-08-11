package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.SelectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOptionGroupRequest(@NotBlank String name, @NotNull SelectionType selectionType, Integer displayOrder) {

    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
