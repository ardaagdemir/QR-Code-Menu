package com.qrmenu.menu.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameMenuCategoryRequest(@NotBlank String name) {
}
