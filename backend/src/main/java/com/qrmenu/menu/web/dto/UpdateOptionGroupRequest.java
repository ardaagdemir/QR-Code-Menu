package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.SelectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateOptionGroupRequest(@NotBlank String name, @NotNull SelectionType selectionType) {
}
