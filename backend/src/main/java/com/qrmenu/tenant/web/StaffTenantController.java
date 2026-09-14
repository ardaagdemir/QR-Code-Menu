package com.qrmenu.tenant.web;

import com.qrmenu.ordering.OrderingService;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.BranchBusinessHours;
import com.qrmenu.tenant.Business;
import com.qrmenu.tenant.BusinessContact;
import com.qrmenu.tenant.RestaurantTable;
import com.qrmenu.tenant.TableLocation;
import com.qrmenu.tenant.TableQrToken;
import com.qrmenu.tenant.TenantService;
import com.qrmenu.tenant.TenantService.BranchBusinessHoursEntry;
import com.qrmenu.tenant.web.dto.BranchBusinessHoursResponse;
import com.qrmenu.tenant.web.dto.BranchResponse;
import com.qrmenu.tenant.web.dto.BulkCreateTablesRequest;
import com.qrmenu.tenant.web.dto.BusinessContactResponse;
import com.qrmenu.tenant.web.dto.BusinessResponse;
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
import com.qrmenu.tenant.web.dto.UpdateBusinessNameRequest;
import com.qrmenu.tenant.web.dto.UpdateBusinessSettingsRequest;
import com.qrmenu.tenant.web.dto.UpdateTableRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final OrderingService orderingService;
    private final StaffAuthService staffAuthService;

    public StaffTenantController(TenantService tenantService, OrderingService orderingService, StaffAuthService staffAuthService) {
        this.tenantService = tenantService;
        this.orderingService = orderingService;
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

    @PostMapping("/business/name")
    public BusinessResponse updateBusinessName(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody UpdateBusinessNameRequest request) {
        StaffContext context = resolveContext(sessionCookie, Permission.BUSINESS_SETTINGS_MANAGE);
        Business business = tenantService.updateBusinessName(context.businessId(), request.name(), context.staffUserId());
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
                request.dailyReportRecipient(), request.monthlyReportRecipient(), context.staffUserId());
        return ResponseEntity.ok(toResponse(contact));
    }

    @DeleteMapping("/business/contacts/{contactId}")
    public ResponseEntity<Void> deleteBusinessContact(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID contactId) {
        StaffContext context = resolveContext(sessionCookie, Permission.BUSINESS_SETTINGS_MANAGE);
        tenantService.deleteBusinessContact(context.businessId(), contactId, context.staffUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/branch", "/branches"})
    public List<BranchResponse> listBranches(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = resolveActiveContext(sessionCookie, null, Permission.BRANCH_MANAGE, Permission.ORDERING_TOGGLE);
        return List.of(toResponse(tenantService.getBranch(context.businessId(), context.activeBranchId())));
    }

    @PostMapping({"/branch/address", "/branches/{branchId}/address"})
    public ResponseEntity<BranchResponse> setAddress(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @Valid @RequestBody SetAddressRequest request) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        Branch branch = tenantService.setAddress(context.businessId(), context.activeBranchId(), request.address(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
    }

    @PostMapping({"/branch/timezone", "/branches/{branchId}/timezone"})
    public ResponseEntity<BranchResponse> setTimezone(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @Valid @RequestBody SetBranchTimezoneRequest request) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        Branch branch = tenantService.setBranchTimezone(context.businessId(), context.activeBranchId(), request.timezone(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
    }

    @PostMapping({"/branch/store-acceptance-timeout", "/branches/{branchId}/store-acceptance-timeout"})
    public ResponseEntity<BranchResponse> setStoreAcceptanceTimeout(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @Valid @RequestBody SetStoreAcceptanceTimeoutRequest request) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        Branch branch = tenantService.setStoreAcceptanceTimeoutSeconds(
                context.businessId(), context.activeBranchId(), request.timeoutSeconds(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
    }

    @PostMapping({"/branch/ordering-enabled", "/branches/{branchId}/ordering-enabled"})
    public ResponseEntity<BranchResponse> setOrderingEnabled(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @Valid @RequestBody SetOrderingEnabledRequest request) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.ORDERING_TOGGLE, branchId);
        Branch branch = tenantService.setOrderingEnabled(context.businessId(), context.activeBranchId(), request.enabled(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
    }

    @PostMapping({"/branch/delivery-model", "/branches/{branchId}/delivery-model"})
    public ResponseEntity<BranchResponse> setDeliveryModel(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @Valid @RequestBody SetDeliveryModelRequest request) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        Branch branch =
                tenantService.setDeliveryModel(context.businessId(), context.activeBranchId(), request.deliveryModel(), context.staffUserId());
        return ResponseEntity.ok(toResponse(branch));
    }

    @GetMapping({"/branch/business-hours", "/branches/{branchId}/business-hours"})
    public List<BranchBusinessHoursResponse> getBusinessHours(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId) {
        StaffContext context = resolveActiveContext(sessionCookie, branchId, Permission.BRANCH_MANAGE, Permission.ORDERING_TOGGLE);
        return tenantService.getBranchBusinessHours(context.businessId(), context.activeBranchId()).stream().map(StaffTenantController::toResponse).toList();
    }

    @PostMapping({"/branch/business-hours", "/branches/{branchId}/business-hours"})
    public List<BranchBusinessHoursResponse> setBusinessHours(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @Valid @RequestBody SetBranchBusinessHoursRequest request) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        List<BranchBusinessHoursEntry> entries = request.days().stream()
                .map(day -> new BranchBusinessHoursEntry(day.dayOfWeek(), day.openingTime(), day.closingTime(), day.closed()))
                .toList();
        return tenantService.setBranchBusinessHours(context.businessId(), context.activeBranchId(), entries, context.staffUserId()).stream()
                .map(StaffTenantController::toResponse)
                .toList();
    }

    @GetMapping({"/tables", "/branches/{branchId}/tables"})
    public List<TableResponse> listTables(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        return tenantService.listTables(context.businessId(), context.activeBranchId()).stream().map(this::toResponse).toList();
    }

    @PostMapping({"/tables", "/branches/{branchId}/tables"})
    public ResponseEntity<TableResponse> createTable(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @Valid @RequestBody CreateTableRequest request) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        TableLocation location = request.location() != null ? request.location() : TableLocation.INDOOR;
        RestaurantTable table = tenantService.createTable(
                context.businessId(), context.activeBranchId(), request.label(), location, request.capacity());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(table));
    }

    /** Otomatik Oluştur: tek transaction içinde "<prefix> <n>" desenli count kadar masa - bkz. TenantService.bulkCreateTables. */
    @PostMapping({"/tables/bulk", "/branches/{branchId}/tables/bulk"})
    public ResponseEntity<List<TableResponse>> bulkCreateTables(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @Valid @RequestBody BulkCreateTablesRequest request) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        List<TableResponse> created = tenantService
                .bulkCreateTables(
                        context.businessId(), context.activeBranchId(), request.location(), request.count(),
                        request.namePrefix(), request.capacity())
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping({"/tables/{tableId}", "/branches/{branchId}/tables/{tableId}"})
    public ResponseEntity<TableResponse> updateTable(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID tableId,
            @Valid @RequestBody UpdateTableRequest request) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        RestaurantTable table = tenantService.updateTable(
                context.businessId(), context.activeBranchId(), tableId, request.label(), request.location(),
                request.capacity(), context.staffUserId());
        return ResponseEntity.ok(toResponse(table));
    }

    /** Hard-delete: only a table with no TableVisit history at all (409 TableHasVisitHistoryException otherwise, see archive below). */
    @DeleteMapping({"/tables/{tableId}", "/branches/{branchId}/tables/{tableId}"})
    public ResponseEntity<Void> deleteTable(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID tableId) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        tenantService.deleteTable(context.businessId(), context.activeBranchId(), tableId, context.staffUserId());
        return ResponseEntity.noContent().build();
    }

    /** Archive: for a table with visit/order history. Rejects with 409 if a visit or order is still active (see OrderingService.archiveTable). */
    @PostMapping({"/tables/{tableId}/archive", "/branches/{branchId}/tables/{tableId}/archive"})
    public ResponseEntity<TableResponse> archiveTable(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID tableId) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        RestaurantTable table = orderingService.archiveTable(context.businessId(), context.activeBranchId(), tableId, context.staffUserId());
        return ResponseEntity.ok(toResponse(table));
    }

    /** Re-enables an archived table for QR check-in - never auto-generates a QR, staff regenerates one explicitly if needed. */
    @PostMapping({"/tables/{tableId}/reactivate", "/branches/{branchId}/tables/{tableId}/reactivate"})
    public ResponseEntity<TableResponse> reactivateTable(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID tableId) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.BRANCH_MANAGE, branchId);
        RestaurantTable table = tenantService.reactivateTable(
                context.businessId(), context.activeBranchId(), tableId, context.staffUserId());
        return ResponseEntity.ok(toResponse(table));
    }

    @PostMapping({"/tables/{tableId}/qr-tokens", "/branches/{branchId}/tables/{tableId}/qr-tokens"})
    public ResponseEntity<QrTokenResponse> regenerateQrToken(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID tableId) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.QR_MANAGE, branchId);
        TableQrToken token = tenantService.regenerateQrToken(context.businessId(), context.activeBranchId(), tableId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(token));
    }

    @GetMapping({"/tables/{tableId}/qr-tokens/active", "/branches/{branchId}/tables/{tableId}/qr-tokens/active"})
    public ResponseEntity<QrTokenResponse> getActiveQrToken(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID tableId) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.QR_MANAGE, branchId);
        TableQrToken token = tenantService.getActiveQrToken(context.businessId(), context.activeBranchId(), tableId);
        return ResponseEntity.ok(toResponse(token));
    }

    @PostMapping("/qr-tokens/{qrTokenId}/revoke")
    public ResponseEntity<Void> revokeQrToken(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID qrTokenId) {
        StaffContext context = resolveActiveContext(sessionCookie, Permission.QR_MANAGE);
        tenantService.revokeQrToken(context.businessId(), context.activeBranchId(), qrTokenId, context.staffUserId());
        return ResponseEntity.noContent().build();
    }

    private StaffContext resolveContext(String sessionCookie, Permission required) {
        return staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie), required);
    }

    private StaffContext resolveActiveContext(String sessionCookie, Permission required) {
        return staffAuthService.resolveStaffContextForActiveBranch(StaffCookieSupport.parseSessionId(sessionCookie), required);
    }

    private StaffContext resolveActiveContext(String sessionCookie, Permission required, UUID requestedBranchId) {
        StaffContext context = resolveActiveContext(sessionCookie, required);
        if (requestedBranchId != null && !context.activeBranchId().equals(requestedBranchId)) {
            return staffAuthService.resolveStaffContextForBranch(
                    StaffCookieSupport.parseSessionId(sessionCookie), required, requestedBranchId);
        }
        return context;
    }

    /** anyOf variant for read endpoints multiple roles reach via different permissions - see listBranches/getBusinessHours. */
    private StaffContext resolveActiveContext(String sessionCookie, UUID requestedBranchId, Permission... anyOf) {
        UUID sessionId = StaffCookieSupport.parseSessionId(sessionCookie);
        StaffContext context = staffAuthService.resolveStaffContextForActiveBranch(sessionId, anyOf);
        if (requestedBranchId != null && !context.activeBranchId().equals(requestedBranchId)) {
            return staffAuthService.resolveStaffContextForBranch(sessionId, requestedBranchId, anyOf);
        }
        return context;
    }

    private BranchResponse toResponse(Branch branch) {
        boolean openNow = tenantService.isOpenNow(branch.getBusinessId(), branch.getId());
        return new BranchResponse(
                branch.getId(), branch.getBusinessId(), branch.getName(), branch.isActive(), branch.isOrderingEnabled(), openNow,
                branch.getAddress(), branch.getTimezone(), branch.getDeliveryModel().name(), branch.getStoreAcceptanceTimeoutSeconds());
    }

    private BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(), business.getName(), business.isActive(), business.getDefaultCurrency(),
                business.getDefaultTimeZone(), business.getCreatedAt());
    }

    private static BusinessContactResponse toResponse(BusinessContact contact) {
        return new BusinessContactResponse(
                contact.getId(), contact.getName(), contact.getPhone(), contact.getEmail(), contact.isWhatsappEnabled(),
                contact.isDailyReportRecipient(), contact.isMonthlyReportRecipient());
    }

    private static BranchBusinessHoursResponse toResponse(BranchBusinessHours hours) {
        return new BranchBusinessHoursResponse(hours.getDayOfWeek(), hours.getOpeningTime(), hours.getClosingTime(), hours.isClosed());
    }

    private TableResponse toResponse(RestaurantTable table) {
        return new TableResponse(
                table.getId(), table.getBusinessId(), table.getBranchId(), table.getLabel(), table.isActive(),
                table.getLocation(), table.getCapacity());
    }

    private QrTokenResponse toResponse(TableQrToken token) {
        return new QrTokenResponse(
                token.getId(), token.getTableId(), token.getToken(), token.getStatus().name(), token.getCreatedAt());
    }
}
