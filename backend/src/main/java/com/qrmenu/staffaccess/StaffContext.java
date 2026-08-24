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
     *
     * <p>No role-based exemption here on purpose, including PLATFORM_ADMIN: StaffAuthService.
     * createStaffUser auto-assigns it every branch of its home business, so a business with more
     * than one branch gives a PLATFORM_ADMIN 2+ branchIds - there is no single correct "the"
     * active branch to silently pick among them, and guessing one would be exactly the kind of
     * ambiguous branch-scoping bug this method exists to prevent. A PLATFORM_ADMIN's real
     * cross-business work always goes through an explicit caller-supplied businessId/branchId
     * instead (see PlatformAdminBusinessController, and StaffAuthService.
     * resolveStaffContextForActiveBranch's own PLATFORM_ADMIN short-circuit, which never calls
     * this method at all). This throws for any caller - any role - that doesn't resolve to
     * exactly one branch.
     */
    public UUID activeBranchId() {
        if (branchIds.size() != 1) {
            throw new StaffPermissionDeniedException("Staff user must have exactly one active branch");
        }
        return branchIds.iterator().next();
    }
}
