package com.qrmenu.staffaccess.web.dto;

import java.util.List;
import java.util.UUID;

public record StaffContextResponse(
        UUID staffUserId,
        UUID businessId,
        String email,
        String role,
        List<UUID> branchIds,
        String businessName,
        List<BranchSummary> branches) {

    public record BranchSummary(UUID id, String name) {
    }
}
