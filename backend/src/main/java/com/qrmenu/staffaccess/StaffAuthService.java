package com.qrmenu.staffaccess;

import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.common.web.StaffAuthenticationRequiredException;
import com.qrmenu.common.web.StaffPermissionDeniedException;
import com.qrmenu.staffaccess.repository.StaffSessionRepository;
import com.qrmenu.staffaccess.repository.StaffUserBranchRepository;
import com.qrmenu.staffaccess.repository.StaffUserRepository;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.TenantService;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the staff-access module (Section 3). Mirrors
 * CustomerSessionService's shape (resolve-then-authorize as one call at the top of
 * every controller method) but adds a permission check on top of session resolution -
 * StaffPrincipal-style DI was deliberately not used (see pom.xml's spring-security-crypto
 * comment); every controller explicitly reads the cookie and calls in here, exactly
 * like CartController does with CustomerSessionService.
 */
@Service
public class StaffAuthService {

    /** No CONFIRMED/RECOMMENDED value in the docs for staff sessions - reusing TableVisit's upper bound (Section 5) as a reasonable shift-length default. */
    public static final Duration SESSION_TTL = Duration.ofHours(6);

    private final StaffUserRepository staffUserRepository;
    private final StaffUserBranchRepository staffUserBranchRepository;
    private final StaffSessionRepository staffSessionRepository;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public StaffAuthService(
            StaffUserRepository staffUserRepository,
            StaffUserBranchRepository staffUserBranchRepository,
            StaffSessionRepository staffSessionRepository,
            TenantService tenantService,
            AuditService auditService) {
        this.staffUserRepository = staffUserRepository;
        this.staffUserBranchRepository = staffUserBranchRepository;
        this.staffSessionRepository = staffSessionRepository;
        this.tenantService = tenantService;
        this.auditService = auditService;
    }

    @Transactional
    public LoginResult login(String email, String rawPassword) {
        StaffUser staffUser = staffUserRepository
                .findByEmail(email)
                .filter(StaffUser::isActive)
                .orElseThrow(() -> new StaffAuthenticationRequiredException("Invalid email or password"));
        if (!passwordEncoder.matches(rawPassword, staffUser.getPasswordHash())) {
            throw new StaffAuthenticationRequiredException("Invalid email or password");
        }
        requireBusinessActive(staffUser);
        StaffSession session = staffSessionRepository.save(new StaffSession(staffUser.getId()));
        return new LoginResult(session.getId(), toContext(staffUser));
    }

    @Transactional
    public void logout(UUID sessionId) {
        if (sessionId != null) {
            staffSessionRepository.deleteById(sessionId);
        }
    }

    /** Authenticated, but no specific permission required yet (e.g. GET /me). */
    @Transactional(readOnly = true)
    public StaffContext resolveStaffContext(UUID sessionId) {
        if (sessionId == null) {
            throw new StaffAuthenticationRequiredException("No staff session");
        }
        StaffSession session = staffSessionRepository
                .findById(sessionId)
                .filter(s -> s.getLastActivityAt().isAfter(Instant.now().minus(SESSION_TTL)))
                .orElseThrow(() -> new StaffAuthenticationRequiredException("Staff session expired or not found"));
        StaffUser staffUser = staffUserRepository
                .findById(session.getStaffUserId())
                .filter(StaffUser::isActive)
                .orElseThrow(() -> new StaffAuthenticationRequiredException("Staff user not found or inactive"));
        requireBusinessActive(staffUser);
        return toContext(staffUser);
    }

    /** Authenticated + must hold the given Permission; no active-branch resolution is performed here. */
    @Transactional(readOnly = true)
    public StaffContext resolveStaffContext(UUID sessionId, Permission required) {
        StaffContext context = resolveStaffContext(sessionId);
        requirePermission(context, required);
        return context;
    }

    /** Authenticated + must hold the given Permission + that permission must apply to this specific branch. */
    @Transactional(readOnly = true)
    public StaffContext resolveStaffContextForBranch(UUID sessionId, Permission required, UUID branchId) {
        StaffContext context = resolveStaffContext(sessionId, required);
        if (context.role() != StaffRole.PLATFORM_ADMIN
                && !tenantService.requireBusinessIdForBranch(branchId).equals(context.businessId())) {
            throw new StaffPermissionDeniedException("Not authorized for branch: " + branchId);
        }
        if (!context.canAccessBranch(branchId)) {
            throw new StaffPermissionDeniedException("Not authorized for branch: " + branchId);
        }
        return context;
    }

    /**
     * Resolve the only branch that staff-web is allowed to operate on. PLATFORM_ADMIN is
     * exempt from this "one active branch" resolution entirely - it's the one cross-business
     * role, and (since createStaffUser auto-assigns it every branch of its home business) a
     * business with more than one branch always gives it 2+ branchIds, so there is no single
     * branch here to unambiguously validate. Endpoints that let PLATFORM_ADMIN through must
     * either not need a specific branch (e.g. StaffMenuController's ALL_BRANCHES bulk-assign,
     * which special-cases the role and never calls StaffContext.activeBranchId()) or resolve one
     * explicitly via resolveStaffContextForBranch/the caller-supplied businessId path used by
     * PlatformAdminBusinessController - never by silently guessing one here.
     */
    @Transactional(readOnly = true)
    public StaffContext resolveStaffContextForActiveBranch(UUID sessionId, Permission required) {
        StaffContext context = resolveStaffContext(sessionId, required);
        if (context.role() == StaffRole.PLATFORM_ADMIN) {
            return context;
        }
        UUID branchId = context.activeBranchId();
        if (!tenantService.requireBusinessIdForBranch(branchId).equals(context.businessId())) {
            throw new StaffPermissionDeniedException("Active branch does not belong to staff business");
        }
        return context;
    }

    public void requirePermission(StaffContext context, Permission required) {
        if (!context.role().hasPermission(required)) {
            throw new StaffPermissionDeniedException("Missing permission: " + required);
        }
    }

    /**
     * Read-oriented variant of {@link #resolveStaffContextForActiveBranch(UUID, Permission)} for
     * endpoints multiple roles need to reach for different reasons (e.g. GET /branch is read by
     * BUSINESS_ADMIN via BRANCH_MANAGE and by BRANCH_MANAGER via ORDERING_TOGGLE, since the
     * latter only ever needs the ordering-enabled toggle on that same screen - see
     * StaffTenantController's class Javadoc).
     */
    @Transactional(readOnly = true)
    public StaffContext resolveStaffContextForActiveBranch(UUID sessionId, Permission... anyOf) {
        StaffContext context = resolveStaffContext(sessionId);
        requireAnyPermission(context, anyOf);
        if (context.role() == StaffRole.PLATFORM_ADMIN) {
            return context;
        }
        UUID branchId = context.activeBranchId();
        if (!tenantService.requireBusinessIdForBranch(branchId).equals(context.businessId())) {
            throw new StaffPermissionDeniedException("Active branch does not belong to staff business");
        }
        return context;
    }

    /** Branch-scoped counterpart of {@link #resolveStaffContextForActiveBranch(UUID, Permission...)}. */
    @Transactional(readOnly = true)
    public StaffContext resolveStaffContextForBranch(UUID sessionId, UUID branchId, Permission... anyOf) {
        StaffContext context = resolveStaffContext(sessionId);
        requireAnyPermission(context, anyOf);
        if (context.role() != StaffRole.PLATFORM_ADMIN
                && !tenantService.requireBusinessIdForBranch(branchId).equals(context.businessId())) {
            throw new StaffPermissionDeniedException("Not authorized for branch: " + branchId);
        }
        if (!context.canAccessBranch(branchId)) {
            throw new StaffPermissionDeniedException("Not authorized for branch: " + branchId);
        }
        return context;
    }

    private void requireAnyPermission(StaffContext context, Permission... anyOf) {
        for (Permission permission : anyOf) {
            if (context.role().hasPermission(permission)) {
                return;
            }
        }
        throw new StaffPermissionDeniedException("Missing permission: one of " + Arrays.toString(anyOf));
    }

    /** actorStaffUserId is null for the /internal/** bootstrap API, which has no logged-in staff session yet. */
    @Transactional
    public StaffUser createStaffUser(
            UUID businessId, String email, String rawPassword, StaffRole role, List<UUID> branchIds, UUID actorStaffUserId) {
        // PLATFORM_ADMIN oversees the whole business, not one branch - caller-supplied
        // branchIds is ignored and replaced with every branch the business currently has
        // (see enforce_user_facing_staff_single_branch, which deliberately allows
        // PLATFORM_ADMIN more than one staff_user_branch row). This also keeps
        // StaffContext.activeBranchId() resolvable for single-branch businesses instead
        // of throwing when a PLATFORM_ADMIN has zero branch assignments.
        List<UUID> effectiveBranchIds = role == StaffRole.PLATFORM_ADMIN
                ? tenantService.listBranches(businessId).stream().map(Branch::getId).toList()
                : branchIds;
        if (role != StaffRole.PLATFORM_ADMIN && effectiveBranchIds.size() != 1) {
            throw new IllegalArgumentException("User-facing staff users must be assigned to exactly one branch");
        }
        for (UUID branchId : effectiveBranchIds) {
            if (!tenantService.requireBusinessIdForBranch(branchId).equals(businessId)) {
                throw new StaffPermissionDeniedException("Branch does not belong to staff business");
            }
        }
        StaffUser staffUser =
                staffUserRepository.save(new StaffUser(businessId, email, passwordEncoder.encode(rawPassword), role));
        for (UUID branchId : effectiveBranchIds) {
            staffUserBranchRepository.save(new StaffUserBranch(staffUser.getId(), branchId));
        }
        auditService.record(
                businessId, actorStaffUserId, "StaffUser", staffUser.getId(), "CREATED", Map.of("role", role.name()));
        return staffUser;
    }

    @Transactional(readOnly = true)
    public List<StaffUser> listStaffUsers(UUID businessId) {
        return staffUserRepository.findAllByBusinessIdOrderByCreatedAtAsc(businessId);
    }

    /** Business-scoped staff screen (StaffUserController): PLATFORM_ADMIN is filtered out
     * entirely, never just hidden client-side - it's auto-assigned to every branch of its home
     * business (see createStaffUser), so without this filter it would otherwise show up as a
     * regular team member in a business's own Personel list. */
    @Transactional(readOnly = true)
    public List<StaffUser> listStaffUsers(UUID businessId, UUID branchId) {
        return listStaffUsers(businessId).stream()
                .filter(user -> user.getRole() != StaffRole.PLATFORM_ADMIN)
                .filter(user -> hasEffectiveBranchAssignment(user, branchId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<UUID> getBranchIds(UUID staffUserId) {
        return staffUserBranchRepository.findAllByStaffUserId(staffUserId).stream()
                .map(StaffUserBranch::getBranchId)
                .collect(Collectors.toSet());
    }

    /** Platform admin panel: cross-business, no branch scoping - never targets a PLATFORM_ADMIN
     * (platform admin management is deliberately kept out of this panel, see resetPasswordAsPlatformAdmin). */
    @Transactional
    public void deactivateStaffUserAsPlatformAdmin(UUID businessId, UUID staffUserId, UUID actorStaffUserId) {
        StaffUser staffUser = requireNonPlatformAdminTarget(businessId, staffUserId);
        staffUser.deactivate();
        staffUserRepository.save(staffUser);
        auditService.record(businessId, actorStaffUserId, "StaffUser", staffUserId, "DEACTIVATED", Map.of());
    }

    @Transactional
    public void activateStaffUserAsPlatformAdmin(UUID businessId, UUID staffUserId, UUID actorStaffUserId) {
        StaffUser staffUser = requireNonPlatformAdminTarget(businessId, staffUserId);
        staffUser.activate();
        staffUserRepository.save(staffUser);
        auditService.record(businessId, actorStaffUserId, "StaffUser", staffUserId, "ACTIVATED", Map.of());
    }

    /** Platform admin panel role assignment - restricted to the existing non-PLATFORM_ADMIN roles
     * (BUSINESS_ADMIN/BRANCH_MANAGER/CASHIER); neither the target nor the new role may be PLATFORM_ADMIN.
     * PLATFORM_ADMIN accounts are only ever created through the /internal/** bootstrap API. */
    @Transactional
    public void changeStaffUserRole(UUID businessId, UUID staffUserId, StaffRole newRole, UUID actorStaffUserId) {
        if (newRole == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot grant PLATFORM_ADMIN through the platform admin panel");
        }
        StaffUser staffUser = requireNonPlatformAdminTarget(businessId, staffUserId);
        staffUser.changeRole(newRole);
        staffUserRepository.save(staffUser);
        auditService.record(
                businessId, actorStaffUserId, "StaffUser", staffUserId, "ROLE_CHANGED", Map.of("newRole", newRole.name()));
    }

    private StaffUser requireNonPlatformAdminTarget(UUID businessId, UUID staffUserId) {
        StaffUser staffUser = staffUserRepository
                .findByIdAndBusinessId(staffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));
        if (staffUser.getRole() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("PLATFORM_ADMIN accounts are not managed through this panel");
        }
        return staffUser;
    }

    @Transactional
    public void deactivateStaffUser(UUID businessId, UUID branchId, UUID staffUserId) {
        StaffUser staffUser = staffUserRepository
                .findByIdAndBusinessId(staffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));
        if (staffUser.getRole() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot deactivate a PLATFORM_ADMIN through business-scoped staff management");
        }
        if (!hasEffectiveBranchAssignment(staffUser, branchId)) {
            throw new ResourceNotFoundException("Staff user not found in active branch: " + staffUserId);
        }
        staffUser.deactivate();
        staffUserRepository.save(staffUser);
    }

    /** Self-service: requires the caller's own current password, keeps their own session alive
     * but ends every other session of theirs (Section: password change must revoke stolen sessions). */
    @Transactional
    public void changePassword(UUID staffUserId, UUID currentSessionId, String currentPassword, String newPassword, String confirmNewPassword) {
        validatePasswordsMatch(newPassword, confirmNewPassword);
        StaffUser staffUser = staffUserRepository
                .findById(staffUserId)
                .filter(StaffUser::isActive)
                .orElseThrow(() -> new StaffAuthenticationRequiredException("Staff user not found or inactive"));
        if (!passwordEncoder.matches(currentPassword, staffUser.getPasswordHash())) {
            throw new StaffAuthenticationRequiredException("Current password is incorrect");
        }
        staffUser.updatePasswordHash(passwordEncoder.encode(newPassword));
        staffUserRepository.save(staffUser);
        staffSessionRepository.deleteAllByStaffUserIdAndIdNot(staffUserId, currentSessionId);
        auditService.record(staffUser.getBusinessId(), staffUserId, "StaffUser", staffUserId, "PASSWORD_CHANGED", Map.of());
    }

    /** Admin-triggered: never touches the caller's own account (self-reset must go through
     * changePassword, which actually verifies the current password), never reaches a
     * PLATFORM_ADMIN target, and never reactivates a disabled user. Revokes every one of the
     * target's sessions since there is no "current" session of theirs to preserve. */
    @Transactional
    public void resetPassword(
            UUID businessId, UUID branchId, UUID actorStaffUserId, UUID targetStaffUserId, String newPassword, String confirmNewPassword) {
        if (actorStaffUserId.equals(targetStaffUserId)) {
            throw new IllegalArgumentException("Use change-password to update your own password");
        }
        validatePasswordsMatch(newPassword, confirmNewPassword);
        StaffUser target = staffUserRepository
                .findByIdAndBusinessId(targetStaffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + targetStaffUserId));
        if (target.getRole() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot reset a PLATFORM_ADMIN password through business-scoped staff management");
        }
        if (!hasEffectiveBranchAssignment(target, branchId)) {
            throw new ResourceNotFoundException("Staff user not found in active branch: " + targetStaffUserId);
        }
        target.updatePasswordHash(passwordEncoder.encode(newPassword));
        staffUserRepository.save(target);
        staffSessionRepository.deleteAllByStaffUserId(targetStaffUserId);
        auditService.record(businessId, actorStaffUserId, "StaffUser", targetStaffUserId, "PASSWORD_RESET", Map.of());
    }

    /** Platform admin panel counterpart to resetPassword - no branch scoping (platform admin isn't
     * tied to any one business's branch set), but still never targets a PLATFORM_ADMIN account
     * (see requireNonPlatformAdminTarget) - platform admins manage their own password exclusively
     * through changePassword. */
    @Transactional
    public void resetPasswordAsPlatformAdmin(
            UUID businessId, UUID actorStaffUserId, UUID targetStaffUserId, String newPassword, String confirmNewPassword) {
        validatePasswordsMatch(newPassword, confirmNewPassword);
        StaffUser target = requireNonPlatformAdminTarget(businessId, targetStaffUserId);
        target.updatePasswordHash(passwordEncoder.encode(newPassword));
        staffUserRepository.save(target);
        staffSessionRepository.deleteAllByStaffUserId(targetStaffUserId);
        auditService.record(businessId, actorStaffUserId, "StaffUser", targetStaffUserId, "PASSWORD_RESET", Map.of());
    }

    private static void validatePasswordsMatch(String newPassword, String confirmNewPassword) {
        if (!newPassword.equals(confirmNewPassword)) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }
    }

    private boolean hasEffectiveBranchAssignment(StaffUser staffUser, UUID branchId) {
        return getBranchIds(staffUser.getId()).contains(branchId);
    }

    /** PLATFORM_ADMIN is exempt - it's the one cross-business role, and its own StaffUser.businessId
     * is just a resolvable home branch (see PlatformAdminBusinessController), so deactivating that
     * business must not lock a platform admin out of the panel used to reactivate it. */
    private void requireBusinessActive(StaffUser staffUser) {
        if (staffUser.getRole() == StaffRole.PLATFORM_ADMIN) {
            return;
        }
        if (!tenantService.getBusiness(staffUser.getBusinessId()).isActive()) {
            throw new StaffAuthenticationRequiredException("Business is deactivated");
        }
    }

    private StaffContext toContext(StaffUser staffUser) {
        Set<UUID> branchIds = getBranchIds(staffUser.getId());
        if (staffUser.getRole() != StaffRole.PLATFORM_ADMIN && branchIds.size() != 1) {
            throw new StaffPermissionDeniedException("Staff user must have exactly one active branch assignment");
        }
        return new StaffContext(staffUser.getId(), staffUser.getBusinessId(), staffUser.getEmail(), staffUser.getRole(), branchIds);
    }

    public record LoginResult(UUID sessionId, StaffContext context) {
    }
}
