package com.qrmenu.platformadmin;

import com.qrmenu.common.web.BranchHasActiveOrdersException;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.TenantService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the platform-admin branch-deactivate flow across the tenant and ordering
 * modules - tenant cannot depend on ordering directly (would cycle back, since ordering
 * already depends on tenant), so this lives in platformadmin instead, the same way
 * OrderControlController orchestrates ordering+refund across their own module boundary.
 *
 * <p>Everything below runs in one physical transaction: TenantService.getBranchForUpdate
 * takes a PESSIMISTIC_WRITE lock on the branch row first, then OrderingService.hasActiveOrders
 * is read under that same lock, then the deactivate write happens - so a concurrent
 * order/payment reaching TenantService.assertOrderingCurrentlyAllowed (which takes the
 * identical lock) can never slip a newly-active order past this check, and this check can
 * never deactivate a branch out from under an order that's mid-transition. Whichever side
 * gets the lock first wins; the other sees the fully-committed result once it proceeds.
 */
@Service
public class PlatformAdminBranchService {

    private final TenantService tenantService;
    private final OrderingService orderingService;

    public PlatformAdminBranchService(TenantService tenantService, OrderingService orderingService) {
        this.tenantService = tenantService;
        this.orderingService = orderingService;
    }

    @Transactional
    public Branch deactivateBranch(UUID businessId, UUID branchId, UUID actorStaffUserId) {
        Branch branch = tenantService.getBranchForUpdate(businessId, branchId);
        if (orderingService.hasActiveOrders(branch.getId())) {
            throw new BranchHasActiveOrdersException(
                    "Branch has an order still in progress, cannot deactivate: " + branchId);
        }
        return tenantService.deactivateLockedBranch(branch, actorStaffUserId);
    }
}
