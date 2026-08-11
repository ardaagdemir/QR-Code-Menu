package com.qrmenu.tenant.web;

import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.RestaurantTable;
import com.qrmenu.tenant.TableQrToken;
import com.qrmenu.tenant.TenantService;
import com.qrmenu.tenant.web.dto.BranchResponse;
import com.qrmenu.tenant.web.dto.CreateBranchRequest;
import com.qrmenu.tenant.web.dto.CreateTableRequest;
import com.qrmenu.tenant.web.dto.QrTokenResponse;
import com.qrmenu.tenant.web.dto.SetDeliveryModelRequest;
import com.qrmenu.tenant.web.dto.SetOrderingEnabledRequest;
import com.qrmenu.tenant.web.dto.TableResponse;
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
 * Section 4, staff-web admin screens: Branch/Table/QR management (BUSINESS_ADMIN,
 * Permission.BRANCH_MANAGE/QR_MANAGE) plus the ordering-enabled toggle, which
 * BRANCH_MANAGER can also do for their own assigned branches (Permission.ORDERING_TOGGLE,
 * branch-scoped via StaffContext.canAccessBranch - same pattern as
 * KitchenController/RefundController). No businessId path variable - always derived
 * from the session, same reasoning as StaffUserController.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffTenantController {

    private final TenantService tenantService;
    private final StaffAuthService staffAuthService;

    public StaffTenantController(TenantService tenantService, StaffAuthService staffAuthService) {
        this.tenantService = tenantService;
        this.staffAuthService = staffAuthService;
    }

    @GetMapping("/branches")
    public List<BranchResponse> listBranches(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        return tenantService.listBranches(context.businessId()).stream().map(this::toResponse).toList();
    }

    @PostMapping("/branches")
    public ResponseEntity<BranchResponse> createBranch(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateBranchRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        Branch branch = tenantService.createBranch(
                context.businessId(),
                request.name(),
                request.orderingEnabledOrDefault(),
                request.openingTime(),
                request.closingTime(),
                request.deliveryModelOrDefault());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(branch));
    }

    @PostMapping("/branches/{branchId}/ordering-enabled")
    public ResponseEntity<BranchResponse> setOrderingEnabled(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @Valid @RequestBody SetOrderingEnabledRequest request) {
        StaffContext context = staffAuthService.resolveStaffContextForBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.ORDERING_TOGGLE, branchId);
        Branch branch = tenantService.setOrderingEnabled(context.businessId(), branchId, request.enabled(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
    }

    @PostMapping("/branches/{branchId}/delivery-model")
    public ResponseEntity<BranchResponse> setDeliveryModel(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @Valid @RequestBody SetDeliveryModelRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        Branch branch =
                tenantService.setDeliveryModel(context.businessId(), branchId, request.deliveryModel(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
    }

    @GetMapping("/branches/{branchId}/tables")
    public List<TableResponse> listTables(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        return tenantService.listTables(context.businessId(), branchId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/branches/{branchId}/tables")
    public ResponseEntity<TableResponse> createTable(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @Valid @RequestBody CreateTableRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        RestaurantTable table = tenantService.createTable(context.businessId(), branchId, request.label());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(table));
    }

    @PostMapping("/branches/{branchId}/tables/{tableId}/qr-tokens")
    public ResponseEntity<QrTokenResponse> regenerateQrToken(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId) {
        StaffContext context = resolveContext(sessionCookie, Permission.QR_MANAGE);
        TableQrToken token = tenantService.regenerateQrToken(context.businessId(), tableId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(token));
    }

    @GetMapping("/branches/{branchId}/tables/{tableId}/qr-tokens/active")
    public ResponseEntity<QrTokenResponse> getActiveQrToken(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId) {
        StaffContext context = resolveContext(sessionCookie, Permission.QR_MANAGE);
        TableQrToken token = tenantService.getActiveQrToken(context.businessId(), tableId);
        return ResponseEntity.ok(toResponse(token));
    }

    @PostMapping("/qr-tokens/{qrTokenId}/revoke")
    public ResponseEntity<Void> revokeQrToken(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID qrTokenId) {
        StaffContext context = resolveContext(sessionCookie, Permission.QR_MANAGE);
        tenantService.revokeQrToken(context.businessId(), qrTokenId, context.staffUserId());
        return ResponseEntity.noContent().build();
    }

    private StaffContext resolveContext(String sessionCookie, Permission required) {
        return staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie), required);
    }

    private BranchResponse toResponse(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getBusinessId(),
                branch.getName(),
                branch.isOrderingEnabled(),
                branch.getOpeningTime(),
                branch.getClosingTime(),
                branch.getDeliveryModel().name());
    }

    private TableResponse toResponse(RestaurantTable table) {
        return new TableResponse(table.getId(), table.getBusinessId(), table.getBranchId(), table.getLabel());
    }

    private QrTokenResponse toResponse(TableQrToken token) {
        return new QrTokenResponse(
                token.getId(), token.getTableId(), token.getToken(), token.getStatus().name(), token.getCreatedAt());
    }
}
