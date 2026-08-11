package com.qrmenu.tenant;

import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.OrderingNotAllowedException;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.tenant.repository.BranchRepository;
import com.qrmenu.tenant.repository.BusinessRepository;
import com.qrmenu.tenant.repository.RestaurantTableRepository;
import com.qrmenu.tenant.repository.TableQrTokenRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
    private final AuditService auditService;

    public TenantService(
            BusinessRepository businessRepository,
            BranchRepository branchRepository,
            RestaurantTableRepository tableRepository,
            TableQrTokenRepository qrTokenRepository,
            AuditService auditService) {
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.tableRepository = tableRepository;
        this.qrTokenRepository = qrTokenRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Business createBusiness(String name) {
        return businessRepository.save(new Business(name));
    }

    @Transactional
    public Branch createBranch(
            UUID businessId,
            String name,
            boolean orderingEnabled,
            LocalTime openingTime,
            LocalTime closingTime,
            DeliveryModel deliveryModel) {
        businessRepository
                .findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));
        return branchRepository.save(new Branch(businessId, name, orderingEnabled, openingTime, closingTime, deliveryModel));
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

    @Transactional(readOnly = true)
    public List<Branch> listBranches(UUID businessId) {
        return branchRepository.findAllByBusinessIdOrderByNameAsc(businessId);
    }

    @Transactional(readOnly = true)
    public List<RestaurantTable> listTables(UUID businessId, UUID branchId) {
        branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        return tableRepository.findAllByBranchIdOrderByLabelAsc(branchId);
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
     * ödeme başlamadan hemen önce eklenmeli"). Branch.orderingEnabled/openingTime/
     * closingTime existed since Milestone 2 but were never read anywhere until now
     * (cart add/remove deliberately skips this soft check - Section 1.1's "katmanlı
     * savunma": soft check at add-to-cart time is out of scope, this is the one
     * authoritative check). A null opening/closing time means no hours restriction
     * (only orderingEnabled applies); an overnight window (opening after closing, e.g.
     * 18:00-02:00) is treated as wrapping past midnight.
     */
    @Transactional(readOnly = true)
    public void assertOrderingCurrentlyAllowed(UUID businessId, UUID branchId) {
        Branch branch = branchRepository
                .findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found for business: " + branchId));
        if (!branch.isOrderingEnabled()) {
            throw new OrderingNotAllowedException("Branch is not currently accepting orders: " + branchId);
        }
        LocalTime opening = branch.getOpeningTime();
        LocalTime closing = branch.getClosingTime();
        if (opening != null && closing != null && !isWithinHours(LocalTime.now(), opening, closing)) {
            throw new OrderingNotAllowedException("Branch is outside its ordering hours: " + branchId);
        }
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
        return new TableReference(
                business.getId(), branch.getId(), table.getId(), business.getName(), branch.getName(), table.getLabel());
    }
}
