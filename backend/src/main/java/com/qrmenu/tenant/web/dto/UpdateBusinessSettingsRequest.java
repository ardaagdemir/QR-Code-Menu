package com.qrmenu.tenant.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateBusinessSettingsRequest(@NotBlank String defaultCurrency, @NotBlank String defaultTimeZone) {
}
