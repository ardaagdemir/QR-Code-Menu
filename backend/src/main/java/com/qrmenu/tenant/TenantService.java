package com.qrmenu.tenant;

import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.BusinessUnavailableException;
import com.qrmenu.common.web.OrderingNotAllowedException;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.tenant.repository.BranchBusinessHoursRepository;
import com.qrmenu.tenant.repository.BranchRepository;
import com.qrmenu.tenant.repository.BusinessContactRepository;
import com.qrmenu.tenant.repository.BusinessRepository;
import com.qrmenu.tenant.repository.RestaurantTableRepository;
import com.qrmenu.tenant.repository.TableQrTokenRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the tenant module. Every method that takes a businessId performs
 * an explicit ownership check before touching the child entity - this is the "açık
 * sorgu + servis kontrolü" tenant isolation discipline from Section 2, applied even to
 * the internal/PLATFORM_ADMIN API (a wrong businessId in a request should still fail,
 * not silently operate on the wrong tenant's data).
 */
@Service
public class TenantService {

    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableQrTokenRepository qrTokenRepository;
    private final BranchBusinessHoursRepository branchBusinessHoursRepository;
    private final BusinessContactRepository businessContactRepository;
    private final AuditService auditService;
    private final Clock clock;

    public TenantService(
            BusinessRepository businessRepository,
            BranchRepository branchRepository,
            RestaurantTableRepository tableRepository,
            TableQrTokenRepository qrTokenRepository,
            BranchBusinessHoursRepository branchBusinessHoursRepository,
            BusinessContactRepository businessContactRepository,
            AuditService auditService,
            Clock clock) {
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.tableRepository = tableRepository;
        this.qrTokenRepository = qrTokenRepository;
        this.branchBusinessHoursRepository = branchBusinessHoursRepository;
        this.businessContactRepository = businessContactRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** actorStaffUserId is null for the /internal/** bootstrap API, which has no logged-in staff session yet. */
    @Transactional
    public Business createBusiness(String name, UUID actorStaffUserId) {
        Business business = businessRepository.save(new Business(name));
        auditService.record(business.getId(), actorStaffUserId, "Business", business.getId(), "CREATED", Map.of("name", name));
        return business;
    }

    @Transactional(readOnly = true)
    public Business getBusiness(UUID businessId) {
        return businessRepository
                .findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));
    }

    /** Platform admin panel: cross-business listing, not scoped to any single tenant. */
    @Transactional(readOnly = true)
    public List<Business> listBusinesses() {
        return businessRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Business activateBusiness(UUID businessId, UUID actorStaffUserId) {
        Business business = getBusiness(businessId);
        business.activate();
        businessRepository.save(business);
        auditService.record(businessId, actorStaffUserId, "Business", businessId, "ACTIVATED", Map.of());
        return business;
    }

    @Transactional
    public Business deactivateBusiness(UUID businessId, UUID actorStaffUserId) {
        Business business = getBusiness(businessId);
        business.deactivate();
        businessRepository.save(business);
        auditService.record(businessId, actorStaffUserId, "Business", businessId, "DEACTIVATED", Map.of());
        return business;
    }

    /** Gap-analysis #6, Section 12.1: BUSINESS_ADMIN-editable default currency/timezone fallback. */
    @Transactional
    public Business updateBusinessSettings(UUID businessId, String defaultCurrency, String defaultTimeZone, UUID actorStaffUserId) {
        Business business = getBusiness(businessId);
        business.setSettings(defaultCurrency, defaultTimeZone);
        businessRepository.save(business);
        auditService.record(businessId, actorStaffUserId, "Business", businessId, "SETTINGS_CHANGED", Map.of());
        return business;
    }

    /** actorStaffUserId is null for the /internal/** bootstrap API, which has no logged-in staff session yet. */
    @Transactional
    public Branch createBranch(
            UUID businessId, String name, boolean orderingEnabled, String address, DeliveryModel deliveryModel,
            UUID actorStaffUserId) {
        businessRepository
                .findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));
        Branch branch = branchRepository.save(new Branch(businessId, name, orderingEnabled, address, deliveryModel));
        auditService.record(businessId, actorStaffUserId, "Branch", branch.getId(), "CREATED", Map.of("name", name));
        return branch;
    }

    /** Platform admin panel: edits a branch's basic info (name/address) - never a hard delete. */
    @Transactional
    public Branch updateBranchInfo(UUID businessId, UUID branchId, String name, String address, UUID actorStaffUserId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        branch.rename(name);
        branch.setAddress(address);
        branchRepository.save(branch);
        auditService.record(businessId, actorStaffUserId, "Branch", branch.getId(), "INFO_UPDATED", Map.of("name", name));
        return branch;
    }

    /** Platform admin panel: reactivation never conflicts with in-flight orders, so no locking is needed here. */
    @Transactional
    public Branch activateBranch(UUID businessId, UUID branchId, UUID actorStaffUserId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        branch.activate();
        branchRepository.save(branch);
        auditService.record(businessId, actorStaffUserId, "Branch", branch.getId(), "ACTIVATED", Map.of());
        return branch;
    }

    /**
     * Locks the branch row (PESSIMISTIC_WRITE) for the platform-admin deactivate flow -
     * held until the caller's transaction commits, so a concurrent
     * assertOrderingCurrentlyAllowed call (same lock) cannot let a new order become
     * "active" in the gap between the caller's active-order check and its actual
     * deactivate write. See PlatformAdminBranchService.deactivateBranch, the only caller.
     */
    @Transactional
    public Branch getBranchForUpdate(UUID businessId, UUID branchId) {
        return branchRepository
                .findByIdAndBusinessIdForUpdate(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
    }

    /** Deactivates a branch already loaded (and locked) via getBranchForUpdate - see that method's Javadoc. */
    @Transactional
    public Branch deactivateLockedBranch(Branch branch, UUID actorStaffUserId) {
        branch.deactivate();
        branchRepository.save(branch);
        auditService.record(branch.getBusinessId(), actorStaffUserId, "Branch", branch.getId(), "DEACTIVATED", Map.of());
        return branch;
    }

    @Transactional
    public RestaurantTable createTable(UUID businessId, UUID branchId, String label) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        return tableRepository.save(new RestaurantTable(branch.getBusinessId(), branch.getId(), label));
    }

    /**
     * Revokes the table's current ACTIVE token (if any) and issues a new one - this is
     * the "manuel yeniden üretim" behaviour from Section 2 (no automatic rotation). The
     * revoke is flushed before the insert so the partial unique index never has to
     * arbitrate between two ACTIVE rows for the same table.
     */
    @Transactional
    public TableQrToken regenerateQrToken(UUID businessId, UUID tableId) {
        RestaurantTable table = tableRepository
                .findByIdAndBusinessId(tableId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found for business: " + tableId));

        qrTokenRepository.findByTableIdAndStatus(table.getId(), QrTokenStatus.ACTIVE).ifPresent(existing -> {
            existing.revoke();
            qrTokenRepository.saveAndFlush(existing);
        });

        return qrTokenRepository.save(new TableQrToken(table.getBusinessId(), table.getId(), QrTokenGenerator.generate()));
    }

    @Transactional
    public TableQrToken regenerateQrToken(UUID businessId, UUID branchId, UUID tableId) {
        requireTableInBranch(businessId, branchId, tableId);
        return regenerateQrToken(businessId, tableId);
    }

    /** Masa etiketini değiştirir - QR token'ı etkilemez, aynı masaya bağlı kalır. */
    @Transactional
    public RestaurantTable renameTable(UUID businessId, UUID branchId, UUID tableId, String label, UUID actorStaffUserId) {
        RestaurantTable table = requireTableInBranch(businessId, branchId, tableId);
        table.rename(label);
        RestaurantTable saved = tableRepository.save(table);
        auditService.record(businessId, actorStaffUserId, "RestaurantTable", saved.getId(), "RENAMED", Map.of("label", label));
        return saved;
    }

    @Transactional
    public void revokeQrToken(UUID businessId, UUID qrTokenId, UUID actorStaffUserId) {
        TableQrToken token = qrTokenRepository
                .findByIdAndBusinessId(qrTokenId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("QR token not found for business: " + qrTokenId));
        token.revoke();
        qrTokenRepository.save(token);
        auditService.record(
                businessId, actorStaffUserId, "TableQrToken", token.getId(), "REVOKED",
                Map.of("tableId", token.getTableId().toString()));
    }

    @Transactional
    public void revokeQrToken(UUID businessId, UUID branchId, UUID qrTokenId, UUID actorStaffUserId) {
        TableQrToken token = qrTokenRepository
                .findByIdAndBusinessId(qrTokenId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("QR token not found for business: " + qrTokenId));
        requireTableInBranch(businessId, branchId, token.getTableId());
        revokeQrToken(businessId, qrTokenId, actorStaffUserId);
    }

    @Transactional(readOnly = true)
    public List<Branch> listBranches(UUID businessId) {
        return branchRepository.findAllByBusinessIdOrderByNameAsc(businessId);
    }

    /** Gap-analysis #9: system-wide branch scan for the daily-close scheduler (all businesses, not one). */
    @Transactional(readOnly = true)
    public List<Branch> listAllBranches() {
        return branchRepository.findAll();
    }

    /** Gap-analysis #8 reporting: a single branch (name/timezone) scoped to its business. */
    @Transactional(readOnly = true)
    public Branch getBranch(UUID businessId, UUID branchId) {
        return branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
    }

    @Transactional(readOnly = true)
    public List<RestaurantTable> listTables(UUID businessId, UUID branchId) {
        branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        return tableRepository.findAllByBranchIdOrderByLabelAsc(branchId);
    }

    private RestaurantTable requireTableInBranch(UUID businessId, UUID branchId, UUID tableId) {
        RestaurantTable table = tableRepository
                .findByIdAndBusinessId(tableId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found for business: " + tableId));
        if (!table.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Table not found in active branch: " + tableId);
        }
        return table;
    }

    /** Bölüm 19.3 kasa/KDS kartlarındaki masa etiketi için: tekil, tenant-scoped, throw etmeyen lookup. */
    @Transactional(readOnly = true)
    public Optional<RestaurantTable> findTable(UUID businessId, UUID tableId) {
        return tableRepository.findByIdAndBusinessId(tableId, businessId);
    }

    /** Section 9, Milestone 8: BUSINESS_ADMIN/BRANCH_MANAGER can toggle ordering on/off for their branch. */
    @Transactional
    public Branch setOrderingEnabled(UUID businessId, UUID branchId, boolean enabled, UUID actorStaffUserId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        branch.setOrderingEnabled(enabled);
        branchRepository.save(branch);
        auditService.record(
                businessId, actorStaffUserId, "Branch", branch.getId(), "ORDERING_TOGGLED",
                Map.of("orderingEnabled", enabled));
        return branch;
    }

    /** Section 9, Milestone 9: BUSINESS_ADMIN can switch a branch between CUSTOMER_PICKUP and WAITER_DELIVERY. */
    @Transactional
    public Branch setDeliveryModel(UUID businessId, UUID branchId, DeliveryModel deliveryModel, UUID actorStaffUserId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        branch.setDeliveryModel(deliveryModel);
        branchRepository.save(branch);
        auditService.record(
                businessId, actorStaffUserId, "Branch", branch.getId(), "DELIVERY_MODEL_CHANGED",
                Map.of("deliveryModel", deliveryModel.name()));
        return branch;
    }

    /** Section 12.2: opsiyonel adres alanı. */
    @Transactional
    public Branch setAddress(UUID businessId, UUID branchId, String address, UUID actorStaffUserId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        branch.setAddress(address);
        branchRepository.save(branch);
        auditService.record(businessId, actorStaffUserId, "Branch", branch.getId(), "ADDRESS_CHANGED", Map.of());
        return branch;
    }

    /** Section 12.2: opsiyonel şube saat dilimi - null ise tüketen taraf Business.defaultTimeZone'a düşer. */
    @Transactional
    public Branch setBranchTimezone(UUID businessId, UUID branchId, String timezone, UUID actorStaffUserId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        branch.setTimezone(timezone);
        branchRepository.save(branch);
        auditService.record(businessId, actorStaffUserId, "Branch", branch.getId(), "TIMEZONE_CHANGED", Map.of());
        return branch;
    }

    /** Section 6/27: kasa kabul bekleme timeout'u - BUSINESS_ADMIN/BRANCH_MANAGER değiştirebilir. */
    @Transactional
    public Branch setStoreAcceptanceTimeoutSeconds(UUID businessId, UUID branchId, int timeoutSeconds, UUID actorStaffUserId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        branch.setStoreAcceptanceTimeoutSeconds(timeoutSeconds);
        branchRepository.save(branch);
        auditService.record(
                businessId, actorStaffUserId, "Branch", branch.getId(), "STORE_ACCEPTANCE_TIMEOUT_CHANGED",
                Map.of("timeoutSeconds", timeoutSeconds));
        return branch;
    }

    /** Section 10.1 kasa dashboard: siparişin ne zaman gecikmiş/kritik sayılacağını belirleyen branch ayarı. */
    @Transactional(readOnly = true)
    public int getStoreAcceptanceTimeoutSeconds(UUID branchId) {
        return branchRepository
                .findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId))
                .getStoreAcceptanceTimeoutSeconds();
    }

    /** Section 12.3: bir işletmenin birden fazla sahibi/rapor alıcısı olabilir. */
    @Transactional
    public BusinessContact createBusinessContact(
            UUID businessId, String name, String phone, String email, boolean whatsappEnabled,
            boolean dailyReportRecipient, boolean monthlyReportRecipient, UUID actorStaffUserId) {
        getBusiness(businessId);
        BusinessContact contact = businessContactRepository.save(new BusinessContact(
                businessId, name, phone, email, whatsappEnabled, dailyReportRecipient, monthlyReportRecipient));
        auditService.record(businessId, actorStaffUserId, "BusinessContact", contact.getId(), "CREATED", Map.of());
        return contact;
    }

    @Transactional
    public BusinessContact updateBusinessContact(
            UUID businessId, UUID contactId, String name, String phone, String email, boolean whatsappEnabled,
            boolean dailyReportRecipient, boolean monthlyReportRecipient, boolean active, UUID actorStaffUserId) {
        BusinessContact contact = businessContactRepository
                .findByIdAndBusinessId(contactId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business contact not found: " + contactId));
        contact.update(name, phone, email, whatsappEnabled, dailyReportRecipient, monthlyReportRecipient, active);
        businessContactRepository.save(contact);
        auditService.record(businessId, actorStaffUserId, "BusinessContact", contact.getId(), "UPDATED", Map.of());
        return contact;
    }

    @Transactional(readOnly = true)
    public List<BusinessContact> listBusinessContacts(UUID businessId) {
        return businessContactRepository.findAllByBusinessIdOrderByNameAsc(businessId);
    }

    public record BranchBusinessHoursEntry(DayOfWeek dayOfWeek, LocalTime openingTime, LocalTime closingTime, boolean closed) {
    }

    /**
     * Gap-analysis #4: replaces the whole weekly schedule in one call (delete-then-
     * insert, within this one transaction) rather than a per-day upsert - simpler than
     * reconciling partial updates, and a "set my hours" admin screen naturally submits
     * the whole week at once anyway. Days not included in `entries` end up with no row
     * at all, i.e. unrestricted for that day (see BranchBusinessHours Javadoc).
     */
    @Transactional
    public List<BranchBusinessHours> setBranchBusinessHours(
            UUID businessId, UUID branchId, List<BranchBusinessHoursEntry> entries, UUID actorStaffUserId) {
        branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        branchBusinessHoursRepository.deleteAllByBranchId(branchId);
        // Hibernate's default flush order runs inserts before deletes, so without this
        // explicit flush the inserts below race the queued deletes and hit
        // uq_branch_business_hours_branch_day for any day kept across the update.
        branchBusinessHoursRepository.flush();
        List<BranchBusinessHours> saved = entries.stream()
                .map(entry -> branchBusinessHoursRepository.save(new BranchBusinessHours(
                        businessId, branchId, entry.dayOfWeek(), entry.openingTime(), entry.closingTime(), entry.closed())))
                .toList();
        auditService.record(
                businessId, actorStaffUserId, "Branch", branchId, "BUSINESS_HOURS_CHANGED", Map.of("dayCount", entries.size()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<BranchBusinessHours> getBranchBusinessHours(UUID businessId, UUID branchId) {
        branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        return branchBusinessHoursRepository.findAllByBranchId(branchId);
    }

    /** Used by order tracking (customer-facing) to show the delivery model - Section 4, screen #9. */
    @Transactional(readOnly = true)
    public DeliveryModel getDeliveryModel(UUID branchId) {
        return branchRepository
                .findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId))
                .getDeliveryModel();
    }

    @Transactional(readOnly = true)
    public TableQrToken getActiveQrToken(UUID businessId, UUID tableId) {
        RestaurantTable table = tableRepository
                .findByIdAndBusinessId(tableId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found for business: " + tableId));
        return qrTokenRepository
                .findByTableIdAndStatus(table.getId(), QrTokenStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active QR token for table: " + tableId));
    }

    @Transactional(readOnly = true)
    public TableQrToken getActiveQrToken(UUID businessId, UUID branchId, UUID tableId) {
        requireTableInBranch(businessId, branchId, tableId);
        return getActiveQrToken(businessId, tableId);
    }

    /**
     * Used by other modules (e.g. menu) that need to validate a branchId belongs to a
     * businessId before writing branch-scoped data, without reaching into tenant's
     * repositories directly (Section 2, enforced by ModuleBoundaryTest).
     */
    @Transactional(readOnly = true)
    public void assertBranchBelongsToBusiness(UUID businessId, UUID branchId) {
        branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
    }

    /**
     * Used by public, branch-scoped read endpoints (e.g. the public menu) that only
     * receive a branchId and need to resolve which business owns it.
     */
    @Transactional(readOnly = true)
    public UUID requireBusinessIdForBranch(UUID branchId) {
        return branchRepository
                .findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId))
                .getBusinessId();
    }

    /** Milestone 7: the merchant name(s) a printable receipt needs to show. */
    @Transactional(readOnly = true)
    public BranchDisplayInfo getBranchDisplayInfo(UUID branchId) {
        Branch branch =
                branchRepository.findById(branchId).orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));
        Business business = businessRepository
                .findById(branch.getBusinessId())
                .orElseThrow(() -> new IllegalStateException("Business missing for branch " + branch.getId()));
        return new BranchDisplayInfo(business.getName(), branch.getName());
    }

    /**
     * The authoritative, pre-payment ordering-allowed check (Section 9, Milestone 5:
     * "Branch ordering-enabled/çalışma saati kontrolünün 'kesin/otoriter' hali ...
     * ödeme başlamadan hemen önce eklenmeli"; gap-analysis #4 moved the hours source
     * from Branch.openingTime/closingTime to per-day BranchBusinessHours). Cart
     * add/remove deliberately skips this soft check - Section 1.1's "katmanlı savunma":
     * soft check at add-to-cart time is out of scope, this is the one authoritative
     * check. No row for today's day-of-week means no hours restriction (only
     * orderingEnabled applies) - see BranchBusinessHours Javadoc; a `closed=true` row
     * blocks ordering outright; an overnight window (opening after closing, e.g.
     * 18:00-02:00) is treated as wrapping past midnight.
     */
    @Transactional
    public void assertOrderingCurrentlyAllowed(UUID businessId, UUID branchId) {
        // PESSIMISTIC_WRITE (not a plain read): this is the same branch-row lock
        // getBranchForUpdate takes for the platform-admin deactivate flow, so the two
        // paths always serialize against each other instead of racing - see that
        // method's Javadoc and BranchRepository.findByIdAndBusinessIdForUpdate.
        Branch branch = branchRepository
                .findByIdAndBusinessIdForUpdate(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        assertBusinessAndBranchActive(getBusiness(businessId), branch);
        if (!branch.isOrderingEnabled()) {
            throw new OrderingNotAllowedException("Branch is not currently accepting orders: " + branchId);
        }
        if (!isWithinConfiguredBusinessHours(branch)) {
            throw new OrderingNotAllowedException("Branch is outside its ordering hours: " + branchId);
        }
    }

    /**
     * Customer-facing gate: a deactivated business or branch (PLATFORM_ADMIN's
     * activate/deactivate, distinct from Branch.orderingEnabled) rejects new check-in,
     * new orders, and new payments with a dedicated status (BusinessUnavailableException
     * -> 503) so customer-web can show "not currently in service" instead of the generic
     * ordering-not-allowed message. Existing order tracking/receipt access never calls
     * this - only new check-in (TenantService.resolveActiveQrToken), new cart items
     * (OrderingService.addItem), and payment start (assertOrderingCurrentlyAllowed above).
     */
    @Transactional(readOnly = true)
    public void assertBusinessAndBranchActive(UUID businessId, UUID branchId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        assertBusinessAndBranchActive(getBusiness(businessId), branch);
    }

    private void assertBusinessAndBranchActive(Business business, Branch branch) {
        if (!business.isActive() || !branch.isActive()) {
            throw new BusinessUnavailableException("Business or branch is not currently in service: " + branch.getId());
        }
    }

    /** Same authoritative gate as assertOrderingCurrentlyAllowed, without throwing - lets
     * staff-web show a branch's real open/closed status instead of just its orderingEnabled
     * toggle, which on its own ignores the configured weekly hours. */
    @Transactional(readOnly = true)
    public boolean isOpenNow(UUID businessId, UUID branchId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        return branch.isOrderingEnabled() && isWithinConfiguredBusinessHours(branch);
    }

    private boolean isWithinConfiguredBusinessHours(Branch branch) {
        // "Right now" and "today" must be the branch's own local time, not whatever
        // zone the server/JVM happens to be running in - a server in UTC checking a
        // Europe/Istanbul branch's hours near local midnight would otherwise enforce
        // the wrong day-of-week's schedule entirely, not just an off-by-a-few-hours error.
        // Reads the current instant through the injected Clock (a real system clock in
        // production; BranchOvernightCarryoverIntegrationTest swaps in a Clock.fixed(...)
        // to make the near-midnight overnight-carryover gate deterministic in tests) rather
        // than calling LocalDate/LocalTime.now(zone) directly.
        ZoneId zone = resolveBranchTimeZone(branch);
        Clock zonedClock = clock.withZone(zone);
        LocalDate today = LocalDate.now(zonedClock);
        LocalTime now = LocalTime.now(zonedClock);
        // An overnight row (e.g. Monday 18:00-02:00) is stored under Monday, but its
        // early-morning tail is physically Tuesday. Without this, a lookup on Tuesday
        // 01:00 would check Tuesday's own row (wrong or absent) instead of honoring the
        // still-open session carried over from Monday night.
        if (isWithinYesterdaysOvernightCarryOver(branch.getId(), today, now)) {
            return true;
        }
        return branchBusinessHoursRepository.findByBranchIdAndDayOfWeek(branch.getId(), today.getDayOfWeek())
                .map(hours -> {
                    if (hours.isClosed()) {
                        return false;
                    }
                    LocalTime opening = hours.getOpeningTime();
                    LocalTime closing = hours.getClosingTime();
                    return opening == null || closing == null || isWithinHours(now, opening, closing);
                })
                .orElse(true);
    }

    private boolean isWithinYesterdaysOvernightCarryOver(UUID branchId, LocalDate today, LocalTime now) {
        return branchBusinessHoursRepository
                .findByBranchIdAndDayOfWeek(branchId, today.minusDays(1).getDayOfWeek())
                .filter(hours -> !hours.isClosed())
                .filter(hours -> hours.getOpeningTime() != null && hours.getClosingTime() != null)
                .filter(hours -> hours.getOpeningTime().isAfter(hours.getClosingTime()))
                .map(hours -> now.isBefore(hours.getClosingTime()))
                .orElse(false);
    }

    /**
     * The single source of truth for "what calendar day/time is it for this branch" -
     * every day-boundary computation (order history, reports, daily close, the
     * ordering-hours gate above) must resolve through this, never invent its own
     * fallback. A branch's own timezone wins when set (Section 12.2); otherwise it
     * defers to its Business's defaultTimeZone (Section 12.1) - never a bare UTC or
     * server/JVM-default guess, since Business.defaultTimeZone is NOT NULL and
     * validated as a real IANA zone at write time (defaults to "Europe/Istanbul" -
     * see Business's single-arg constructor), so there is always a real answer without
     * falling back to UTC.
     */
    public ZoneId resolveBranchTimeZone(Branch branch) {
        if (branch.getTimezone() != null) {
            return ZoneId.of(branch.getTimezone());
        }
        Business business = businessRepository
                .findById(branch.getBusinessId())
                .orElseThrow(() -> new IllegalStateException("Business missing for branch " + branch.getId()));
        return ZoneId.of(business.getDefaultTimeZone());
    }

    private static boolean isWithinHours(LocalTime now, LocalTime opening, LocalTime closing) {
        if (opening.isBefore(closing)) {
            return !now.isBefore(opening) && now.isBefore(closing);
        }
        // Overnight window (e.g. 18:00-02:00): "within hours" is everything except the
        // gap strictly between closing and opening.
        return !now.isBefore(opening) || now.isBefore(closing);
    }

    /**
     * Resolves a scanned QR token to the table/branch/business it belongs to. Only
     * ACTIVE tokens resolve - a revoked or unknown token looks identical to the caller
     * (404), which is exactly the "QR only starts a new TableVisit, and only while
     * still active" guarantee from Section 5.
     */
    @Transactional(readOnly = true)
    public TableReference resolveActiveQrToken(String rawToken) {
        TableQrToken qrToken = qrTokenRepository
                .findByToken(rawToken)
                .filter(t -> t.getStatus() == QrTokenStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("QR token not found or inactive"));
        RestaurantTable table = tableRepository
                .findById(qrToken.getTableId())
                .orElseThrow(() -> new IllegalStateException("Table missing for QR token " + qrToken.getId()));
        Branch branch = branchRepository
                .findById(table.getBranchId())
                .orElseThrow(() -> new IllegalStateException("Branch missing for table " + table.getId()));
        Business business = businessRepository
                .findById(branch.getBusinessId())
                .orElseThrow(() -> new IllegalStateException("Business missing for branch " + branch.getId()));
        // New check-in only - an already-established TableVisit/session keeps working for
        // tracking/receipt access even after the business/branch is deactivated later
        // (see assertBusinessAndBranchActive's Javadoc).
        assertBusinessAndBranchActive(business, branch);
        return new TableReference(
                business.getId(), branch.getId(), table.getId(), business.getName(), branch.getName(), table.getLabel());
    }
}
