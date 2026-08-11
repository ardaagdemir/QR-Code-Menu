package com.qrmenu.staffaccess.web;

import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffUser;
import com.qrmenu.staffaccess.web.dto.CreateStaffUserRequest;
import com.qrmenu.staffaccess.web.dto.StaffUserResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bootstraps a business's first StaffUser (typically a BUSINESS_ADMIN) - same
 * chicken-and-egg reasoning as InternalTenantController creating the first Business:
 * there's no logged-in admin yet to call the session-gated StaffUserController, so this
 * uses the same shared /internal/** admin-token guard (InternalAdminAuthFilter).
 */
@RestController
@RequestMapping("/internal/businesses/{businessId}/staff-users")
class InternalStaffController {

    private final StaffAuthService staffAuthService;

    InternalStaffController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @PostMapping
    ResponseEntity<StaffUserResponse> create(@PathVariable UUID businessId, @Valid @RequestBody CreateStaffUserRequest request) {
        StaffUser created =
                staffAuthService.createStaffUser(businessId, request.email(), request.password(), request.role(), request.branchIdsOrEmpty());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new StaffUserResponse(
                        created.getId(), created.getEmail(), created.getRole().name(), created.isActive(), request.branchIdsOrEmpty(), created.getCreatedAt()));
    }
}
