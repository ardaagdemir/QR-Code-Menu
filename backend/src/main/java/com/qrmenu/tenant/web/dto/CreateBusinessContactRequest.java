package com.qrmenu.tenant.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBusinessContactRequest(
        @NotBlank String name, String phone, String email, boolean whatsappEnabled, boolean dailyReportRecipient,
        boolean monthlyReportRecipient) {
}
