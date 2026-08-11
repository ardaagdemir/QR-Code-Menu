package com.qrmenu.tenant.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTableRequest(@NotBlank String label) {
}
