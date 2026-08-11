package com.qrmenu.chain;

import com.qrmenu.customersession.CustomerSessionService;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.TenantService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap-analysis #7 read-only chain/branch comparison (product-requirements.md Section
 * 13.2), deliberately scoped to non-financial metrics - revenue/refund comparisons
 * belong to the future Reporting module (gap-analysis #8). Has no persistence of its
 * own: it composes TenantService + OrderingService + CustomerSessionService, each
 * already public-facade APIs of their own module (ModuleBoundaryTest-compliant).
 */
@Service
public class ChainComparisonService {

    private static final Duration WINDOW = Duration.ofHours(24);

    private final TenantService tenantService;
    private final OrderingService orderingService;
    private final CustomerSessionService customerSessionService;

    public ChainComparisonService(
            TenantService tenantService, OrderingService orderingService, CustomerSessionService customerSessionService) {
        this.tenantService = tenantService;
        this.orderingService = orderingService;
        this.customerSessionService = customerSessionService;
    }

    @Transactional(readOnly = true)
    public List<BranchComparisonView> compareBranches(UUID businessId) {
        Instant since = Instant.now().minus(WINDOW);
        List<Branch> branches = tenantService.listBranches(businessId);
        return branches.stream()
                .map(branch -> new BranchComparisonView(
                        branch.getId(),
                        branch.getName(),
                        orderingService.countOrdersSince(branch.getId(), since),
                        customerSessionService.countTableVisitsSince(branch.getId(), since)))
                .toList();
    }
}
