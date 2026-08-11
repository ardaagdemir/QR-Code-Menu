package com.qrmenu.staffaccess.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffUserResponse(UUID id, String email, String role, boolean active, List<UUID> branchIds, Instant createdAt) {
}
