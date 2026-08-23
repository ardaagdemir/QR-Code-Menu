package com.qrmenu.staffaccess.web;

import com.qrmenu.common.web.StaffPermissionDeniedException;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.staffaccess.StaffRole;
import com.qrmenu.staffaccess.StaffUser;
import com.qrmenu.staffaccess.web.dto.CreateStaffUserRequest;
import com.qrmenu.staffaccess.web.dto.ResetPasswordRequest;
import com.qrmenu.staffaccess.web.dto.StaffUserResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Section 4, staff-web screen #4: "Personel/Rol yönetimi (kendi işletmesi içinde)" -
 * BUSINESS_ADMIN only (Permission.STAFF_MANAGE), scoped to their own business by
 * StaffContext.businessId() - there's no businessId path variable here on purpose,
 * unlike the /internal/** bootstrap API: a session-authenticated admin can only ever
 * manage their own business, so trusting a caller-supplied businessId would be a
 * cross-tenant escalation bug waiting to happen.
 */
@RestController
@RequestMapping("/api/staff/staff-users")
public class StaffUserController {

    private final StaffAuthService staffAuthService;

    public StaffUserController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @PostMapping
    public ResponseEntity<StaffUserResponse> create(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateStaffUserRequest request) {
        StaffContext context = requireStaffManage(sessionCookie);
        if (request.role() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot grant PLATFORM_ADMIN through business-scoped staff management");
        }
        StaffUser created = staffAuthService.createStaffUser(
                context.businessId(), request.email(), request.password(), request.role(), List.of(context.activeBranchId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created, List.of(context.activeBranchId())));
    }

    @GetMapping
    public List<StaffUserResponse> list(@CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = requireStaffManage(sessionCookie);
        return staffAuthService.listStaffUsers(context.businessId(), context.activeBranchId()).stream()
                .map(staffUser -> toResponse(staffUser, staffAuthService.getBranchIds(staffUser.getId()).stream().toList()))
                .toList();
    }

    @PostMapping("/{staffUserId}/deactivate")
    public ResponseEntity<Void> deactivate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie, @PathVariable UUID staffUserId) {
        StaffContext context = requireStaffManage(sessionCookie);
        staffAuthService.deactivateStaffUser(context.businessId(), context.activeBranchId(), staffUserId);
        return ResponseEntity.noContent().build();
    }

    /** Admin-triggered reset to a new temporary password the admin types themselves (never
     * system-generated, so it never has to round-trip back through a response body). Blocked
     * for the caller's own account (self-reset must go through /api/staff/auth/change-password,
     * which actually verifies the current password) and for staff outside this admin's own
     * branch - both enforced in StaffAuthService.resetPassword. Does not reactivate a disabled user. */
    @PostMapping("/{staffUserId}/reset-password")
    public ResponseEntity<Void> resetPassword(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID staffUserId,
            @Valid @RequestBody ResetPasswordRequest request) {
        StaffContext context = requireStaffManage(sessionCookie);
        staffAuthService.resetPassword(
                context.businessId(),
                context.activeBranchId(),
                context.staffUserId(),
                staffUserId,
                request.newPassword(),
                request.confirmNewPassword());
        return ResponseEntity.noContent().build();
    }

    private StaffContext requireStaffManage(String sessionCookie) {
        return staffAuthService.resolveStaffContextForActiveBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.STAFF_MANAGE);
    }

    private static StaffUserResponse toResponse(StaffUser staffUser, List<UUID> branchIds) {
        return new StaffUserResponse(staffUser.getId(), staffUser.getEmail(), staffUser.getRole().name(), staffUser.isActive(), branchIds, staffUser.getCreatedAt());
    }
}
