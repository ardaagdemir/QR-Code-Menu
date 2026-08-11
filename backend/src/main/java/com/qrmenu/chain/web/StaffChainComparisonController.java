package com.qrmenu.chain.web;

import com.qrmenu.chain.BranchComparisonView;
import com.qrmenu.chain.ChainComparisonService;
import com.qrmenu.chain.web.dto.BranchComparisonResponse;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import java.util.List;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gap-analysis #7: non-financial branch comparison dashboard (last 24h). */
@RestController
@RequestMapping("/api/staff/branches/comparison")
public class StaffChainComparisonController {

    private final ChainComparisonService chainComparisonService;
    private final StaffAuthService staffAuthService;

    public StaffChainComparisonController(ChainComparisonService chainComparisonService, StaffAuthService staffAuthService) {
        this.chainComparisonService = chainComparisonService;
        this.staffAuthService = staffAuthService;
    }

    @GetMapping
    public List<BranchComparisonResponse> compare(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContext(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.BRANCH_MANAGE);
        return chainComparisonService.compareBranches(context.businessId()).stream()
                .map(StaffChainComparisonController::toResponse)
                .toList();
    }

    private static BranchComparisonResponse toResponse(BranchComparisonView view) {
        return new BranchComparisonResponse(view.branchId(), view.branchName(), view.orderCount(), view.tableVisitCount());
    }
}
