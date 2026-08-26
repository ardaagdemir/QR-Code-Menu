package com.qrmenu.audit.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        UUID actorStaffUserId,
        boolean actorAccountDeleted,
        String entityType,
        UUID entityId,
        String action,
        String details,
        Instant createdAt) {
}
