package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.SelectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOptionGroupRequest(
        @NotBlank String name, @NotNull SelectionType selectionType, Boolean required, Integer displayOrder) {

    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }

    /** Omitted -> the pre-"required" behavior: SINGLE groups were mandatory, MULTIPLE optional. */
    public boolean requiredOrDefault() {
        return required == null ? selectionType == SelectionType.SINGLE : required;
    }
}
