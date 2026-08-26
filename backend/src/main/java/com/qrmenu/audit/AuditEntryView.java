package com.qrmenu.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryView(
        UUID id,
        UUID actorStaffUserId,
        boolean actorAccountDeleted,
        String entityType,
        UUID entityId,
        String action,
        String details,
        Instant createdAt) {
}
