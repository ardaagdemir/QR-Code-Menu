package com.qrmenu.announcement.web.dto;

import com.qrmenu.announcement.AnnouncementTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateAnnouncementRequest(
        @NotBlank String title, @NotBlank String message, @NotNull AnnouncementTarget target, Set<UUID> branchIds, Instant expiresAt) {
}
