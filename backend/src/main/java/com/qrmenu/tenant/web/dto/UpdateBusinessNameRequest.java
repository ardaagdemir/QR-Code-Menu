package com.qrmenu.tenant.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateBusinessNameRequest(@NotBlank String name) {
}
