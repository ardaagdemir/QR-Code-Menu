package com.qrmenu.platformadmin.web;

import com.qrmenu.common.web.StaffPermissionDeniedException;
import com.qrmenu.platformadmin.web.dto.ChangeStaffUserRoleRequest;
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
 * Platform Admin Panel - staff user management for any business. Session-authenticated
 * PLATFORM_ADMIN only. Deliberately cannot create, promote to, or otherwise manage
 * PLATFORM_ADMIN accounts - that stays exclusive to the /internal/** bootstrap API, so a
 * compromised or careless platform-admin session can never mint another one of itself
 * through the browser-facing panel. A PLATFORM_ADMIN manages their own password only
 * through the existing self-service /api/staff/auth/change-password.
 */
@RestController
@RequestMapping("/api/platform-admin/businesses/{businessId}/staff-users")
public class PlatformAdminStaffController {

    private final StaffAuthService staffAuthService;

    public PlatformAdminStaffController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @GetMapping
    public List<StaffUserResponse> list(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie, @PathVariable UUID businessId) {
        requirePlatformAdmin(sessionCookie);
        return staffAuthService.listStaffUsers(businessId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<StaffUserResponse> create(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateStaffUserRequest request) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        if (request.role() == StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Cannot create PLATFORM_ADMIN through the platform admin panel");
        }
        StaffUser created = staffAuthService.createStaffUser(
                businessId, request.email(), request.password(), request.role(), request.branchIdsOrEmpty(), context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PostMapping("/{staffUserId}/activate")
    public ResponseEntity<Void> activate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID businessId,
            @PathVariable UUID staffUserId) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        staffAuthService.activateStaffUserAsPlatformAdmin(businessId, staffUserId, context.staffUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{staffUserId}/deactivate")
    public ResponseEntity<Void> deactivate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID businessId,
            @PathVariable UUID staffUserId) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        staffAuthService.deactivateStaffUserAsPlatformAdmin(businessId, staffUserId, context.staffUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{staffUserId}/role")
    public ResponseEntity<Void> changeRole(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID businessId,
            @PathVariable UUID staffUserId,
            @Valid @RequestBody ChangeStaffUserRoleRequest request) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        staffAuthService.changeStaffUserRole(businessId, staffUserId, request.role(), context.staffUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{staffUserId}/reset-password")
    public ResponseEntity<Void> resetPassword(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID businessId,
            @PathVariable UUID staffUserId,
            @Valid @RequestBody ResetPasswordRequest request) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        staffAuthService.resetPasswordAsPlatformAdmin(
                businessId, context.staffUserId(), staffUserId, request.newPassword(), request.confirmNewPassword());
        return ResponseEntity.noContent().build();
    }

    private StaffContext requirePlatformAdmin(String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie));
        if (context.role() != StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Platform admin panel is restricted to PLATFORM_ADMIN");
        }
        return context;
    }

    private StaffUserResponse toResponse(StaffUser staffUser) {
        List<UUID> branchIds = staffAuthService.getBranchIds(staffUser.getId()).stream().toList();
        return new StaffUserResponse(
                staffUser.getId(), staffUser.getEmail(), staffUser.getRole().name(), staffUser.isActive(), branchIds, staffUser.getCreatedAt());
    }
}
