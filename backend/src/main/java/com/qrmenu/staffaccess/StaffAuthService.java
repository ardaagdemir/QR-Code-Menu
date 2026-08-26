package com.qrmenu.staffaccess;

import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.DuplicateEmailException;
import com.qrmenu.common.web.LastActiveBusinessAdminException;
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
import org.springframework.dao.DataIntegrityViolationException;
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
                .findByEmail(normalizeEmail(email))
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
        String normalizedEmail = normalizeEmail(email);
        requireEmailNotTaken(normalizedEmail, null);
        StaffUser staffUser = saveWithDuplicateEmailHandling(
                new StaffUser(businessId, normalizedEmail, passwordEncoder.encode(rawPassword), role));
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
     * (platform admin management is deliberately kept out of this panel, see resetPasswordAsPlatformAdmin).
     * Kills every session of the target: without this, a session created before deactivation would
     * still pass resolveStaffContext's isActive() filter the moment the account is reactivated later -
     * deactivation must not leave a live session dormant, only to spring back to life on reactivate. */
    @Transactional
    public void deactivateStaffUserAsPlatformAdmin(UUID businessId, UUID staffUserId, UUID actorStaffUserId) {
        StaffUser staffUser = requireNonPlatformAdminTarget(businessId, staffUserId);
        requireNotLastActiveBusinessAdmin(businessId, staffUser);
        staffUser.deactivate();
        staffUserRepository.save(staffUser);
        staffSessionRepository.deleteAllByStaffUserId(staffUserId);
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
     * PLATFORM_ADMIN accounts are only ever created through the /internal/** bootstrap API. Also
     * refuses to demote a business's last active BUSINESS_ADMIN (same guard deactivate already
     * enforces - demoting away the role has the identical effect on the business as deactivating). */
    @Transactional
    public void changeStaffUserRole(UUID businessId, UUID staffUserId, StaffRole newRole, UUID actorStaffUserId) {
        if (newRole == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot grant PLATFORM_ADMIN through the platform admin panel");
        }
        StaffUser staffUser = requireNonPlatformAdminTarget(businessId, staffUserId);
        if (newRole != staffUser.getRole()) {
            requireNotLastActiveBusinessAdmin(businessId, staffUser);
        }
        staffUser.changeRole(newRole);
        staffUserRepository.save(staffUser);
        staffSessionRepository.deleteAllByStaffUserId(staffUserId);
        auditService.record(
                businessId, actorStaffUserId, "StaffUser", staffUserId, "ROLE_CHANGED", Map.of("newRole", newRole.name()));
    }

    /** Platform admin panel: irreversible - unlike deactivateStaffUserAsPlatformAdmin this
     * actually removes the StaffUser row. Never targets a PLATFORM_ADMIN (requireNonPlatformAdminTarget)
     * or the acting admin's own account. Sessions/branch assignments are cleared explicitly here
     * rather than via a DB-level cascade (see V33's comment); expense/owner_notification_log FKs
     * fall back to NULL automatically via their own ON DELETE SET NULL. Past audit_log_entry rows
     * this user authored are anonymized (actorStaffUserId -> null, actorAccountDeleted -> true)
     * before the delete so the audit trail survives it, distinguishably from genuine system actions.
     *
     * <p>The target row is loaded via findByIdAndBusinessIdForUpdate (a real SQL FOR UPDATE - see
     * that method's Javadoc for why it's not the JPA PESSIMISTIC_WRITE lock mode), held for the
     * rest of this transaction. That's what stops a login()
     * racing in between the session cleanup below and the actual DELETE from leaving a live session
     * pointing at a user that's either mid-deletion or already gone. */
    @Transactional
    public void hardDeleteStaffUserAsPlatformAdmin(UUID businessId, UUID staffUserId, UUID actorStaffUserId) {
        StaffUser staffUser = staffUserRepository
                .findByIdAndBusinessIdForUpdate(staffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));
        requireNotPlatformAdminRole(staffUser);
        if (staffUserId.equals(actorStaffUserId)) {
            throw new StaffPermissionDeniedException("Cannot hard-delete your own account");
        }
        staffSessionRepository.deleteAllByStaffUserId(staffUserId);
        staffUserBranchRepository.deleteAllByStaffUserId(staffUserId);
        auditService.anonymizeActor(staffUserId);
        String email = staffUser.getEmail();
        StaffRole role = staffUser.getRole();
        staffUserRepository.delete(staffUser);
        auditService.record(
                businessId,
                actorStaffUserId,
                "StaffUser",
                staffUserId,
                "STAFF_USER_HARD_DELETED",
                Map.of("email", email, "role", role.name(), "businessId", businessId.toString()));
    }

    private StaffUser requireNonPlatformAdminTarget(UUID businessId, UUID staffUserId) {
        StaffUser staffUser = staffUserRepository
                .findByIdAndBusinessId(staffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));
        requireNotPlatformAdminRole(staffUser);
        return staffUser;
    }

    private void requireNotPlatformAdminRole(StaffUser staffUser) {
        if (staffUser.getRole() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("PLATFORM_ADMIN accounts are not managed through this panel");
        }
    }

    /** Business-scoped staff screen: never targets the caller's own account (self-deactivate must
     * not be possible from any entry point - self-management belongs exclusively to
     * changePassword) and never leaves a business without an active BUSINESS_ADMIN. Kills every
     * session of the target (see deactivateStaffUserAsPlatformAdmin's Javadoc for why: otherwise a
     * pre-deactivation session would silently come back to life on a later reactivate). */
    @Transactional
    public void deactivateStaffUser(UUID businessId, UUID branchId, UUID actorStaffUserId, UUID staffUserId) {
        if (actorStaffUserId.equals(staffUserId)) {
            throw new StaffPermissionDeniedException("Cannot deactivate your own account");
        }
        StaffUser staffUser = staffUserRepository
                .findByIdAndBusinessId(staffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));
        if (staffUser.getRole() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot deactivate a PLATFORM_ADMIN through business-scoped staff management");
        }
        if (!hasEffectiveBranchAssignment(staffUser, branchId)) {
            throw new ResourceNotFoundException("Staff user not found in active branch: " + staffUserId);
        }
        requireNotLastActiveBusinessAdmin(businessId, staffUser);
        staffUser.deactivate();
        staffUserRepository.save(staffUser);
        staffSessionRepository.deleteAllByStaffUserId(staffUserId);
        auditService.record(businessId, actorStaffUserId, "StaffUser", staffUserId, "DEACTIVATED", Map.of());
    }

    /** Business-scoped reactivate: same self/PLATFORM_ADMIN/branch guards as deactivateStaffUser.
     * StaffUser.activate() never touches passwordHash (see its Javadoc) - only changePassword and
     * resetPassword can do that. No last-active-BUSINESS_ADMIN check: reactivating only ever adds
     * capacity back, never removes it. No session invalidation either: a deactivated user has none
     * left (deactivateStaffUser now clears them), so there is nothing here to revive. */
    @Transactional
    public void activateStaffUser(UUID businessId, UUID branchId, UUID actorStaffUserId, UUID staffUserId) {
        if (actorStaffUserId.equals(staffUserId)) {
            throw new StaffPermissionDeniedException("Cannot manage your own account");
        }
        StaffUser staffUser = staffUserRepository
                .findByIdAndBusinessId(staffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));
        if (staffUser.getRole() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot activate a PLATFORM_ADMIN through business-scoped staff management");
        }
        if (!hasEffectiveBranchAssignment(staffUser, branchId)) {
            throw new ResourceNotFoundException("Staff user not found in active branch: " + staffUserId);
        }
        staffUser.activate();
        staffUserRepository.save(staffUser);
        auditService.record(businessId, actorStaffUserId, "StaffUser", staffUserId, "ACTIVATED", Map.of());
    }

    /** Business-scoped role change: same self/PLATFORM_ADMIN/branch guards as deactivateStaffUser,
     * plus the last-active-BUSINESS_ADMIN guard (demoting the last active BUSINESS_ADMIN has the
     * same effect on the business as deactivating them). Kills every session of the target so a
     * stale session can't keep operating under the old role's permissions. */
    @Transactional
    public void changeStaffUserRoleAsBusinessAdmin(
            UUID businessId, UUID branchId, UUID actorStaffUserId, UUID staffUserId, StaffRole newRole) {
        if (actorStaffUserId.equals(staffUserId)) {
            throw new StaffPermissionDeniedException("Cannot manage your own account");
        }
        if (newRole == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot grant PLATFORM_ADMIN through business-scoped staff management");
        }
        StaffUser staffUser = staffUserRepository
                .findByIdAndBusinessId(staffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));
        if (staffUser.getRole() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot change a PLATFORM_ADMIN's role through business-scoped staff management");
        }
        if (!hasEffectiveBranchAssignment(staffUser, branchId)) {
            throw new ResourceNotFoundException("Staff user not found in active branch: " + staffUserId);
        }
        if (newRole != staffUser.getRole()) {
            requireNotLastActiveBusinessAdmin(businessId, staffUser);
        }
        staffUser.changeRole(newRole);
        staffUserRepository.save(staffUser);
        staffSessionRepository.deleteAllByStaffUserId(staffUserId);
        auditService.record(
                businessId, actorStaffUserId, "StaffUser", staffUserId, "ROLE_CHANGED", Map.of("newRole", newRole.name()));
    }

    /** Business-scoped email edit: same self/PLATFORM_ADMIN/branch guards as deactivateStaffUser.
     * Email is normalized (trim+lowercase) before both the app-layer uniqueness check
     * (requireEmailNotTaken) and the write, and the write is still guarded against a concurrent
     * duplicate by the DB's uq_staff_user_email_lower index (see saveWithDuplicateEmailHandling) -
     * the app-layer check alone can't close the race between two simultaneous requests. Kills every
     * session of the target: a stale session must re-login to pick up the corrected identity. Audit
     * metadata deliberately omits the old/new email values - no need to retain that PII once the
     * change is applied. */
    @Transactional
    public void updateStaffUserEmail(UUID businessId, UUID branchId, UUID actorStaffUserId, UUID staffUserId, String newEmail) {
        if (actorStaffUserId.equals(staffUserId)) {
            throw new StaffPermissionDeniedException("Cannot manage your own account");
        }
        StaffUser staffUser = staffUserRepository
                .findByIdAndBusinessId(staffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));
        if (staffUser.getRole() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot edit a PLATFORM_ADMIN's email through business-scoped staff management");
        }
        if (!hasEffectiveBranchAssignment(staffUser, branchId)) {
            throw new ResourceNotFoundException("Staff user not found in active branch: " + staffUserId);
        }
        String normalizedEmail = normalizeEmail(newEmail);
        requireEmailNotTaken(normalizedEmail, staffUserId);
        staffUser.updateEmail(normalizedEmail);
        saveWithDuplicateEmailHandling(staffUser);
        staffSessionRepository.deleteAllByStaffUserId(staffUserId);
        auditService.record(businessId, actorStaffUserId, "StaffUser", staffUserId, "EMAIL_CHANGED", Map.of());
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

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /** App-layer fast-fail so a duplicate returns a clean 409 without ever reaching the DB write -
     * not a substitute for the DB's uq_staff_user_email_lower index (saveWithDuplicateEmailHandling),
     * which is what actually closes the race between two concurrent requests for the same email. */
    private void requireEmailNotTaken(String normalizedEmail, UUID excludingStaffUserId) {
        staffUserRepository.findByEmail(normalizedEmail)
                .filter(existing -> !existing.getId().equals(excludingStaffUserId))
                .ifPresent(existing -> {
                    throw new DuplicateEmailException("Email already in use: " + normalizedEmail);
                });
    }

    private StaffUser saveWithDuplicateEmailHandling(StaffUser staffUser) {
        try {
            return staffUserRepository.save(staffUser);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException("Email already in use: " + staffUser.getEmail());
        }
    }

    /** A business must always retain at least one active BUSINESS_ADMIN, or nobody would be
     * left able to manage its staff, menu, or branches. No-op for any other role or an
     * already-inactive target. */
    private void requireNotLastActiveBusinessAdmin(UUID businessId, StaffUser staffUser) {
        if (staffUser.getRole() != StaffRole.BUSINESS_ADMIN || !staffUser.isActive()) {
            return;
        }
        boolean anotherActiveAdminRemains = listStaffUsers(businessId).stream()
                .anyMatch(other -> other.getRole() == StaffRole.BUSINESS_ADMIN
                        && other.isActive()
                        && !other.getId().equals(staffUser.getId()));
        if (!anotherActiveAdminRemains) {
            throw new LastActiveBusinessAdminException("Cannot deactivate the last active BUSINESS_ADMIN of a business");
        }
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
