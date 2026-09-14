package com.qrmenu.ownernotification.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "Rapor Bildirimleri" ekranı: DAILY için businessDate, MONTHLY için ayın ilk günü olarak
 * {@code period}. {@code dailyCloseReportId} (MONTHLY için null) frontend'in DAILY satırlarında
 * zaten var olan {@code /daily-close/{reportId}/notifications/resend} endpoint'ini
 * çağırabilmesi için taşınır - bu ekran DAILY resend için yeni bir backend akışı eklemez.
 */
public record ReportNotificationResponse(
        UUID id,
        String reportType,
        UUID dailyCloseReportId,
        LocalDate period,
        String recipientEmail,
        String status,
        String errorMessage,
        String triggeredBy,
        Instant attemptedAt) {
}
