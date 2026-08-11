package com.qrmenu.tenant.web.dto;

import java.time.Instant;
import java.util.UUID;

public record BusinessResponse(UUID id, String name, boolean active, Instant createdAt) {
}
