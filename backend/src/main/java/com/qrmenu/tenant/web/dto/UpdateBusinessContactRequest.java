package com.qrmenu.tenant.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateBusinessContactRequest(
        @NotBlank String name, String phone, String email, boolean whatsappEnabled, boolean dailyReportRecipient,
        boolean monthlyReportRecipient, boolean active) {
}
