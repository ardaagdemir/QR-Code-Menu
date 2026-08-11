package com.qrmenu.tenant.web.dto;

import java.util.UUID;

public record BusinessContactResponse(
        UUID id, String name, String phone, String email, boolean whatsappEnabled, boolean dailyReportRecipient,
        boolean monthlyReportRecipient, boolean active) {
}
