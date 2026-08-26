package com.qrmenu.branchprovisioning;

import com.qrmenu.menu.MenuService;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.DeliveryModel;
import com.qrmenu.tenant.TenantService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for "create a branch and give it its default catalog" -
 * InternalTenantController and PlatformAdminBusinessController both go through this instead
 * of calling TenantService.createBranch and MenuService.assignActiveCatalogToBranch
 * back-to-back themselves. Wrapping both calls in one @Transactional method means a
 * provisioning failure rolls back the branch insert too - no half-created branch left behind
 * with an empty customer menu and no way to retry. Composes TenantService + MenuService, each
 * already a public-facade API of its own module (ModuleBoundaryTest-compliant) - same pattern
 * as ChainComparisonService.
 */
@Service
public class BranchProvisioningService {

    private final TenantService tenantService;
    private final MenuService menuService;

    public BranchProvisioningService(TenantService tenantService, MenuService menuService) {
        this.tenantService = tenantService;
        this.menuService = menuService;
    }

    /** actorStaffUserId is null for the /internal/** bootstrap API, which has no logged-in staff session yet. */
    @Transactional
    public Branch createBranchWithDefaultCatalog(
            UUID businessId, String name, boolean orderingEnabled, String address, DeliveryModel deliveryModel,
            UUID actorStaffUserId) {
        Branch branch =
                tenantService.createBranch(businessId, name, orderingEnabled, address, deliveryModel, actorStaffUserId);
        menuService.assignActiveCatalogToBranch(businessId, branch.getId(), actorStaffUserId);
        return branch;
    }
}
