package com.qrmenu.announcement.web.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        UUID businessId,
        String title,
        String message,
        String target,
        Set<UUID> branchIds,
        UUID createdBy,
        Instant createdAt,
        Instant expiresAt) {
}
