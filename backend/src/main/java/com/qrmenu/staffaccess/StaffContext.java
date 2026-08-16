package com.qrmenu.staffaccess;

import java.util.Set;
import java.util.UUID;
import com.qrmenu.common.web.StaffPermissionDeniedException;

/** The resolved, authorized caller - what every staff-facing controller gets back from StaffAuthService. */
public record StaffContext(UUID staffUserId, UUID businessId, String email, StaffRole role, Set<UUID> branchIds) {

    public boolean canAccessBranch(UUID branchId) {
        return role == StaffRole.PLATFORM_ADMIN || branchIds.contains(branchId);
    }

    /**
     * Staff-web roles operate with one server-resolved branch. Keeping the persisted
     * assignment collection for migration compatibility must never turn back into a
     * caller-selectable branch context.
     */
    public UUID activeBranchId() {
        if (role == StaffRole.PLATFORM_ADMIN && branchIds.size() == 1) {
            return branchIds.iterator().next();
        }
        if (branchIds.size() != 1) {
            throw new StaffPermissionDeniedException("Staff user must have exactly one active branch");
        }
        return branchIds.iterator().next();
    }
}
