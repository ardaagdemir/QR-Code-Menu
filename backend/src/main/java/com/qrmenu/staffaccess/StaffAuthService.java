package com.qrmenu.staffaccess;

import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.common.web.StaffAuthenticationRequiredException;
import com.qrmenu.common.web.StaffPermissionDeniedException;
import com.qrmenu.staffaccess.repository.StaffSessionRepository;
import com.qrmenu.staffaccess.repository.StaffUserBranchRepository;
import com.qrmenu.staffaccess.repository.StaffUserRepository;
import com.qrmenu.tenant.TenantService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public StaffAuthService(
            StaffUserRepository staffUserRepository,
            StaffUserBranchRepository staffUserBranchRepository,
            StaffSessionRepository staffSessionRepository,
            TenantService tenantService) {
        this.staffUserRepository = staffUserRepository;
        this.staffUserBranchRepository = staffUserBranchRepository;
        this.staffSessionRepository = staffSessionRepository;
        this.tenantService = tenantService;
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
        return toContext(staffUser);
    }

    /** Authenticated + must hold the given Permission (business-wide, no branch scoping check). */
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

    public void requirePermission(StaffContext context, Permission required) {
        if (!context.role().hasPermission(required)) {
            throw new StaffPermissionDeniedException("Missing permission: " + required);
        }
    }

    @Transactional
    public StaffUser createStaffUser(
            UUID businessId, String email, String rawPassword, StaffRole role, List<UUID> branchIds) {
        StaffUser staffUser =
                staffUserRepository.save(new StaffUser(businessId, email, passwordEncoder.encode(rawPassword), role));
        for (UUID branchId : branchIds) {
            staffUserBranchRepository.save(new StaffUserBranch(staffUser.getId(), branchId));
        }
        return staffUser;
    }

    @Transactional(readOnly = true)
    public List<StaffUser> listStaffUsers(UUID businessId) {
        return staffUserRepository.findAllByBusinessIdOrderByCreatedAtAsc(businessId);
    }

    @Transactional(readOnly = true)
    public Set<UUID> getBranchIds(UUID staffUserId) {
        return staffUserBranchRepository.findAllByStaffUserId(staffUserId).stream()
                .map(StaffUserBranch::getBranchId)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void deactivateStaffUser(UUID businessId, UUID staffUserId) {
        StaffUser staffUser = staffUserRepository
                .findByIdAndBusinessId(staffUserId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffUserId));
        staffUser.deactivate();
        staffUserRepository.save(staffUser);
    }

    private StaffContext toContext(StaffUser staffUser) {
        Set<UUID> branchIds = getBranchIds(staffUser.getId());
        return new StaffContext(staffUser.getId(), staffUser.getBusinessId(), staffUser.getEmail(), staffUser.getRole(), branchIds);
    }

    public record LoginResult(UUID sessionId, StaffContext context) {
    }
}
