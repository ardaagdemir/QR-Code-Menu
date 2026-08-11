package com.qrmenu.tenant.web.dto;

import java.time.Instant;
import java.util.UUID;

public record QrTokenResponse(UUID id, UUID tableId, String token, String status, Instant createdAt) {
}
