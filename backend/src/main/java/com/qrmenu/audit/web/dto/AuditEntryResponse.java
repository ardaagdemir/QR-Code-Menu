package com.qrmenu.audit.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id, UUID actorStaffUserId, String entityType, UUID entityId, String action, String details, Instant createdAt) {
}
