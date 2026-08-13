package com.qrmenu.tenant.web;

import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.BranchBusinessHours;
import com.qrmenu.tenant.Business;
import com.qrmenu.tenant.BusinessContact;
import com.qrmenu.tenant.RestaurantTable;
import com.qrmenu.tenant.TableQrToken;
import com.qrmenu.tenant.TenantService;
import com.qrmenu.tenant.TenantService.BranchBusinessHoursEntry;
import com.qrmenu.tenant.web.dto.BranchBusinessHoursResponse;
import com.qrmenu.tenant.web.dto.BranchResponse;
import com.qrmenu.tenant.web.dto.BusinessContactResponse;
import com.qrmenu.tenant.web.dto.BusinessResponse;
import com.qrmenu.tenant.web.dto.CreateBranchRequest;
import com.qrmenu.tenant.web.dto.CreateBusinessContactRequest;
import com.qrmenu.tenant.web.dto.CreateTableRequest;
import com.qrmenu.tenant.web.dto.QrTokenResponse;
import com.qrmenu.tenant.web.dto.SetAddressRequest;
import com.qrmenu.tenant.web.dto.SetBranchBusinessHoursRequest;
import com.qrmenu.tenant.web.dto.SetBranchTimezoneRequest;
import com.qrmenu.tenant.web.dto.SetDeliveryModelRequest;
import com.qrmenu.tenant.web.dto.SetOrderingEnabledRequest;
import com.qrmenu.tenant.web.dto.SetStoreAcceptanceTimeoutRequest;
import com.qrmenu.tenant.web.dto.TableResponse;
import com.qrmenu.tenant.web.dto.UpdateBusinessContactRequest;
import com.qrmenu.tenant.web.dto.UpdateBusinessSettingsRequest;
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

    @GetMapping("/business")
    public BusinessResponse getBusiness(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = resolveContext(sessionCookie, Permission.BUSINESS_SETTINGS_MANAGE);
        return toResponse(tenantService.getBusiness(context.businessId()));
    }

    @PostMapping("/business/settings")
    public BusinessResponse updateBusinessSettings(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody UpdateBusinessSettingsRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BUSINESS_SETTINGS_MANAGE);
        Business business = tenantService.updateBusinessSettings(
                context.businessId(), request.defaultCurrency(), request.defaultTimeZone(), context.staffUserId());
        return toResponse(business);
    }

    @GetMapping("/business/contacts")
    public List<BusinessContactResponse> listBusinessContacts(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = resolveContext(sessionCookie, Permission.BUSINESS_SETTINGS_MANAGE);
        return tenantService.listBusinessContacts(context.businessId()).stream().map(StaffTenantController::toResponse).toList();
    }

    @PostMapping("/business/contacts")
    public ResponseEntity<BusinessContactResponse> createBusinessContact(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateBusinessContactRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BUSINESS_SETTINGS_MANAGE);
        BusinessContact contact = tenantService.createBusinessContact(
                context.businessId(), request.name(), request.phone(), request.email(), request.whatsappEnabled(),
                request.dailyReportRecipient(), request.monthlyReportRecipient(), context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(contact));
    }

    @PutMapping("/business/contacts/{contactId}")
    public ResponseEntity<BusinessContactResponse> updateBusinessContact(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID contactId,
            @Valid @RequestBody UpdateBusinessContactRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BUSINESS_SETTINGS_MANAGE);
        BusinessContact contact = tenantService.updateBusinessContact(
                context.businessId(), contactId, request.name(), request.phone(), request.email(), request.whatsappEnabled(),
                request.dailyReportRecipient(), request.monthlyReportRecipient(), request.active(), context.staffUserId());
        return ResponseEntity.ok(toResponse(contact));
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
                context.businessId(), request.name(), request.orderingEnabledOrDefault(), request.address(),
                request.deliveryModelOrDefault());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(branch));
    }

    @PostMapping("/branches/{branchId}/address")
    public ResponseEntity<BranchResponse> setAddress(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @Valid @RequestBody SetAddressRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        Branch branch = tenantService.setAddress(context.businessId(), branchId, request.address(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
    }

    @PostMapping("/branches/{branchId}/timezone")
    public ResponseEntity<BranchResponse> setTimezone(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @Valid @RequestBody SetBranchTimezoneRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        Branch branch = tenantService.setBranchTimezone(context.businessId(), branchId, request.timezone(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
    }

    @PostMapping("/branches/{branchId}/store-acceptance-timeout")
    public ResponseEntity<BranchResponse> setStoreAcceptanceTimeout(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @Valid @RequestBody SetStoreAcceptanceTimeoutRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        Branch branch = tenantService.setStoreAcceptanceTimeoutSeconds(
                context.businessId(), branchId, request.timeoutSeconds(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
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

    @GetMapping("/branches/{branchId}/business-hours")
    public List<BranchBusinessHoursResponse> getBusinessHours(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie, @PathVariable UUID branchId) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        return tenantService.getBranchBusinessHours(context.businessId(), branchId).stream().map(StaffTenantController::toResponse).toList();
    }

    @PostMapping("/branches/{branchId}/business-hours")
    public List<BranchBusinessHoursResponse> setBusinessHours(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @Valid @RequestBody SetBranchBusinessHoursRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BRANCH_MANAGE);
        List<BranchBusinessHoursEntry> entries = request.days().stream()
                .map(day -> new BranchBusinessHoursEntry(day.dayOfWeek(), day.openingTime(), day.closingTime(), day.closed()))
                .toList();
        return tenantService.setBranchBusinessHours(context.businessId(), branchId, entries, context.staffUserId()).stream()
                .map(StaffTenantController::toResponse)
                .toList();
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
                branch.getId(), branch.getBusinessId(), branch.getName(), branch.isOrderingEnabled(), branch.getAddress(),
                branch.getTimezone(), branch.getDeliveryModel().name(), branch.getStoreAcceptanceTimeoutSeconds());
    }

    private BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(), business.getName(), business.isActive(), business.getDefaultCurrency(),
                business.getDefaultTimeZone(), business.getCreatedAt());
    }

    private static BusinessContactResponse toResponse(BusinessContact contact) {
        return new BusinessContactResponse(
                contact.getId(), contact.getName(), contact.getPhone(), contact.getEmail(), contact.isWhatsappEnabled(),
                contact.isDailyReportRecipient(), contact.isMonthlyReportRecipient(), contact.isActive());
    }

    private static BranchBusinessHoursResponse toResponse(BranchBusinessHours hours) {
        return new BranchBusinessHoursResponse(hours.getDayOfWeek(), hours.getOpeningTime(), hours.getClosingTime(), hours.isClosed());
    }

    private TableResponse toResponse(RestaurantTable table) {
        return new TableResponse(table.getId(), table.getBusinessId(), table.getBranchId(), table.getLabel());
    }

    private QrTokenResponse toResponse(TableQrToken token) {
        return new QrTokenResponse(
                token.getId(), token.getTableId(), token.getToken(), token.getStatus().name(), token.getCreatedAt());
    }
}
