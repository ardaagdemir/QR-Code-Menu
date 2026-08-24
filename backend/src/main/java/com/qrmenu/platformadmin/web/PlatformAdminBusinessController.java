package com.qrmenu.platformadmin.web;

import com.qrmenu.common.web.StaffPermissionDeniedException;
import com.qrmenu.platformadmin.PlatformAdminBranchService;
import com.qrmenu.platformadmin.web.dto.UpdateBranchInfoRequest;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.staffaccess.StaffRole;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.Business;
import com.qrmenu.tenant.TenantService;
import com.qrmenu.tenant.web.dto.BranchResponse;
import com.qrmenu.tenant.web.dto.BusinessResponse;
import com.qrmenu.tenant.web.dto.CreateBranchRequest;
import com.qrmenu.tenant.web.dto.CreateBusinessRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform Admin Panel - business/branch management. Session-authenticated PLATFORM_ADMIN
 * only, never the /internal/** shared-token bootstrap API (that stays reserved for initial
 * business/PLATFORM_ADMIN provisioning). Every method takes an explicit businessId, exactly
 * like InternalTenantController - PLATFORM_ADMIN is the one cross-business role, so unlike
 * every other staff-facing controller this deliberately never derives businessId from
 * StaffContext.businessId() (a PLATFORM_ADMIN's own StaffUser row happens to carry one, but
 * it must not scope what businesses they can manage here).
 */
@RestController
@RequestMapping("/api/platform-admin/businesses")
public class PlatformAdminBusinessController {

    private final TenantService tenantService;
    private final StaffAuthService staffAuthService;
    private final PlatformAdminBranchService platformAdminBranchService;

    public PlatformAdminBusinessController(
            TenantService tenantService, StaffAuthService staffAuthService, PlatformAdminBranchService platformAdminBranchService) {
        this.tenantService = tenantService;
        this.staffAuthService = staffAuthService;
        this.platformAdminBranchService = platformAdminBranchService;
    }

    @GetMapping
    public List<BusinessResponse> list(@CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        requirePlatformAdmin(sessionCookie);
        return tenantService.listBusinesses().stream().map(PlatformAdminBusinessController::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<BusinessResponse> create(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateBusinessRequest request) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        Business business = tenantService.createBusiness(request.name(), context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(business));
    }

    @GetMapping("/{businessId}")
    public BusinessResponse get(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie, @PathVariable UUID businessId) {
        requirePlatformAdmin(sessionCookie);
        return toResponse(tenantService.getBusiness(businessId));
    }

    @PostMapping("/{businessId}/activate")
    public BusinessResponse activate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie, @PathVariable UUID businessId) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        return toResponse(tenantService.activateBusiness(businessId, context.staffUserId()));
    }

    @PostMapping("/{businessId}/deactivate")
    public BusinessResponse deactivate(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie, @PathVariable UUID businessId) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        return toResponse(tenantService.deactivateBusiness(businessId, context.staffUserId()));
    }

    @GetMapping("/{businessId}/branches")
    public List<BranchResponse> listBranches(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie, @PathVariable UUID businessId) {
        requirePlatformAdmin(sessionCookie);
        return tenantService.listBranches(businessId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/{businessId}/branches")
    public ResponseEntity<BranchResponse> createBranch(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateBranchRequest request) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        Branch branch = tenantService.createBranch(
                businessId, request.name(), request.orderingEnabledOrDefault(), request.address(), request.deliveryModelOrDefault(),
                context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(branch));
    }

    @PutMapping("/{businessId}/branches/{branchId}")
    public BranchResponse updateBranch(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID businessId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchInfoRequest request) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        return toResponse(
                tenantService.updateBranchInfo(businessId, branchId, request.name(), request.address(), context.staffUserId()));
    }

    @PostMapping("/{businessId}/branches/{branchId}/activate")
    public BranchResponse activateBranch(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID businessId,
            @PathVariable UUID branchId) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        return toResponse(tenantService.activateBranch(businessId, branchId, context.staffUserId()));
    }

    @PostMapping("/{businessId}/branches/{branchId}/deactivate")
    public BranchResponse deactivateBranch(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID businessId,
            @PathVariable UUID branchId) {
        StaffContext context = requirePlatformAdmin(sessionCookie);
        return toResponse(platformAdminBranchService.deactivateBranch(businessId, branchId, context.staffUserId()));
    }

    private StaffContext requirePlatformAdmin(String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie));
        if (context.role() != StaffRole.PLATFORM_ADMIN) {
            throw new StaffPermissionDeniedException("Platform admin panel is restricted to PLATFORM_ADMIN");
        }
        return context;
    }

    private static BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(), business.getName(), business.isActive(), business.getDefaultCurrency(),
                business.getDefaultTimeZone(), business.getCreatedAt());
    }

    private BranchResponse toResponse(Branch branch) {
        boolean openNow = tenantService.isOpenNow(branch.getBusinessId(), branch.getId());
        return new BranchResponse(
                branch.getId(), branch.getBusinessId(), branch.getName(), branch.isActive(), branch.isOrderingEnabled(), openNow,
                branch.getAddress(), branch.getTimezone(), branch.getDeliveryModel().name(), branch.getStoreAcceptanceTimeoutSeconds());
    }
}
