package com.qrmenu.announcement.web.dto;

import com.qrmenu.announcement.AnnouncementTarget;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateAnnouncementRequest(
        @NotBlank String title, @NotBlank String message, AnnouncementTarget target, Set<UUID> branchIds, Instant expiresAt) {
}
