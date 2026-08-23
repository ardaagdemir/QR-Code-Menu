package com.qrmenu.tenant.web;

import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.Business;
import com.qrmenu.tenant.RestaurantTable;
import com.qrmenu.tenant.TableQrToken;
import com.qrmenu.tenant.TenantService;
import com.qrmenu.tenant.web.dto.BranchResponse;
import com.qrmenu.tenant.web.dto.BusinessResponse;
import com.qrmenu.tenant.web.dto.CreateBranchRequest;
import com.qrmenu.tenant.web.dto.CreateBusinessRequest;
import com.qrmenu.tenant.web.dto.CreateTableRequest;
import com.qrmenu.tenant.web.dto.QrTokenResponse;
import com.qrmenu.tenant.web.dto.TableResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, PLATFORM_ADMIN-only bootstrap API - no dedicated UI in v1
 * (docs/product-requirements.md Section 3/9). Guarded by InternalAdminAuthFilter for
 * every path under /internal/**.
 */
@RestController
@RequestMapping("/internal/businesses")
class InternalTenantController {

    private final TenantService tenantService;

    InternalTenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    ResponseEntity<BusinessResponse> createBusiness(@Valid @RequestBody CreateBusinessRequest request) {
        Business business = tenantService.createBusiness(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(business));
    }

    @PostMapping("/{businessId}/branches")
    ResponseEntity<BranchResponse> createBranch(
            @PathVariable UUID businessId, @Valid @RequestBody CreateBranchRequest request) {
        Branch branch = tenantService.createBranch(
                businessId, request.name(), request.orderingEnabledOrDefault(), request.address(), request.deliveryModelOrDefault());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(branch));
    }

    @PostMapping("/{businessId}/branches/{branchId}/tables")
    ResponseEntity<TableResponse> createTable(
            @PathVariable UUID businessId, @PathVariable UUID branchId, @Valid @RequestBody CreateTableRequest request) {
        RestaurantTable table = tenantService.createTable(businessId, branchId, request.label());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(table));
    }

    @PostMapping("/{businessId}/tables/{tableId}/qr-tokens")
    ResponseEntity<QrTokenResponse> regenerateQrToken(@PathVariable UUID businessId, @PathVariable UUID tableId) {
        TableQrToken token = tenantService.regenerateQrToken(businessId, tableId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(token));
    }

    @GetMapping("/{businessId}/tables/{tableId}/qr-tokens/active")
    ResponseEntity<QrTokenResponse> getActiveQrToken(@PathVariable UUID businessId, @PathVariable UUID tableId) {
        TableQrToken token = tenantService.getActiveQrToken(businessId, tableId);
        return ResponseEntity.ok(toResponse(token));
    }

    @PostMapping("/{businessId}/qr-tokens/{qrTokenId}/revoke")
    ResponseEntity<Void> revokeQrToken(@PathVariable UUID businessId, @PathVariable UUID qrTokenId) {
        tenantService.revokeQrToken(businessId, qrTokenId, null);
        return ResponseEntity.noContent().build();
    }

    private BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(), business.getName(), business.isActive(), business.getDefaultCurrency(),
                business.getDefaultTimeZone(), business.getCreatedAt());
    }

    private BranchResponse toResponse(Branch branch) {
        boolean openNow = tenantService.isOpenNow(branch.getBusinessId(), branch.getId());
        return new BranchResponse(
                branch.getId(), branch.getBusinessId(), branch.getName(), branch.isOrderingEnabled(), openNow, branch.getAddress(),
                branch.getTimezone(), branch.getDeliveryModel().name(), branch.getStoreAcceptanceTimeoutSeconds());
    }

    private TableResponse toResponse(RestaurantTable table) {
        return new TableResponse(table.getId(), table.getBusinessId(), table.getBranchId(), table.getLabel());
    }

    private QrTokenResponse toResponse(TableQrToken token) {
        return new QrTokenResponse(
                token.getId(), token.getTableId(), token.getToken(), token.getStatus().name(), token.getCreatedAt());
    }
}
