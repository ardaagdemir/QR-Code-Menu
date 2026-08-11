package com.qrmenu.staffaccess;

import java.util.Set;
import java.util.UUID;

/** The resolved, authorized caller - what every staff-facing controller gets back from StaffAuthService. */
public record StaffContext(UUID staffUserId, UUID businessId, String email, StaffRole role, Set<UUID> branchIds) {

    public boolean canAccessBranch(UUID branchId) {
        return role == StaffRole.BUSINESS_ADMIN || role == StaffRole.PLATFORM_ADMIN || branchIds.contains(branchId);
    }
}
