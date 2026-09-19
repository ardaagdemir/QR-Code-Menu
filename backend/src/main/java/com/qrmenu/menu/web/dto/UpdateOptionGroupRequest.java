package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.SelectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** required omitted (null) keeps the group's current value. */
public record UpdateOptionGroupRequest(@NotBlank String name, @NotNull SelectionType selectionType, Boolean required) {
}
