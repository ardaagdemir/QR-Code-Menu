package com.qrmenu.staffaccess.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * activeBranchTimeZone is TenantService.resolveBranchTimeZone's already-resolved result
 * (branch's own timezone, else its business's defaultTimeZone) - never null when
 * activeBranchId is non-null. The frontend must derive "today" from this, not the
 * device/browser clock, so Özet/Kasa/Raporlar agree with the branch-timezone-based
 * reporting endpoints they call (ReportingService, DailyCloseService).
 */
public record StaffContextResponse(
        UUID staffUserId,
        UUID businessId,
        String email,
        String role,
        List<UUID> branchIds,
        String businessName,
        List<BranchSummary> branches,
        UUID activeBranchId,
        String activeBranchName,
        String activeBranchTimeZone) {

    public record BranchSummary(UUID id, String name) {
    }
}
