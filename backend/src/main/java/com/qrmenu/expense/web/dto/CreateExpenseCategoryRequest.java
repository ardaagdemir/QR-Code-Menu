package com.qrmenu.expense.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateExpenseCategoryRequest(@NotBlank String name) {
}
