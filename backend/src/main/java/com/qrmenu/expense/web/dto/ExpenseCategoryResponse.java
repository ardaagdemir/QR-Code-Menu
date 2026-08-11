package com.qrmenu.expense.web.dto;

import java.util.UUID;

public record ExpenseCategoryResponse(UUID id, String name, boolean active) {
}
