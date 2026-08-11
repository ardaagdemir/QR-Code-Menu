package com.qrmenu.ownernotification.web.dto;

import java.time.Instant;
import java.util.UUID;

public record OwnerNotificationLogResponse(
        UUID id,
        String recipientEmail,
        String channel,
        String status,
        String errorMessage,
        String triggeredBy,
        Instant attemptedAt) {
}
