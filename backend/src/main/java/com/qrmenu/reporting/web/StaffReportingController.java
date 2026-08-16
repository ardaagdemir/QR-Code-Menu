package com.qrmenu.reporting.web;

import com.qrmenu.reporting.BranchSalesReportView;
import com.qrmenu.reporting.CategorySalesView;
import com.qrmenu.reporting.ChainSalesReportView;
import com.qrmenu.reporting.HourlySalesView;
import com.qrmenu.reporting.ProductSalesView;
import com.qrmenu.expense.ExpenseService;
import com.qrmenu.reporting.ReportingService;
import com.qrmenu.reporting.web.dto.BranchSalesReportResponse;
import com.qrmenu.reporting.web.dto.CategorySalesResponse;
import com.qrmenu.reporting.web.dto.ChainSalesReportResponse;
import com.qrmenu.reporting.web.dto.HourlySalesResponse;
import com.qrmenu.reporting.web.dto.KitchenFinancialSummaryResponse;
import com.qrmenu.reporting.web.dto.OperatingResultResponse;
import com.qrmenu.reporting.web.dto.ProductSalesResponse;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gap-analysis #8 (product-requirements.md Section 13): branch and chain-wide sales reports. */
@RestController
public class StaffReportingController {

    private final ReportingService reportingService;
    private final ExpenseService expenseService;
    private final StaffAuthService staffAuthService;

    public StaffReportingController(
            ReportingService reportingService, ExpenseService expenseService, StaffAuthService staffAuthService) {
        this.reportingService = reportingService;
        this.expenseService = expenseService;
        this.staffAuthService = staffAuthService;
    }

    /** Section 13.1: Kasa/yönetim panelinde temel metrikler - CASHIER/BRANCH_MANAGER/BUSINESS_ADMIN, kendi şubeleri. */
    @GetMapping({"/api/staff/reports", "/api/staff/branches/{branchId}/reports"})
    public BranchSalesReportResponse branchReport(
            @PathVariable(required = false) UUID branchId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = resolveReportContext(sessionCookie, Permission.REPORT_VIEW, branchId);
        branchId = context.activeBranchId();
        return toResponse(reportingService.getBranchReport(context.businessId(), branchId, from, to));
    }

    /** Section 17: brüt/net satış + onaylı giderler = "Yönetimsel Net Sonuç" - kâr olarak sunulmaz (bkz. OperatingResultResponse javadoc). */
    @GetMapping({"/api/staff/reports/operating-result", "/api/staff/branches/{branchId}/reports/operating-result"})
    public OperatingResultResponse operatingResult(
            @PathVariable(required = false) UUID branchId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = resolveReportContext(sessionCookie, Permission.REPORT_VIEW, branchId);
        branchId = context.activeBranchId();
        BranchSalesReportView report = reportingService.getBranchReport(context.businessId(), branchId, from, to);
        long approvedExpenses = expenseService.sumApprovedExpenses(context.businessId(), branchId, from, to);
        return new OperatingResultResponse(
                branchId,
                report.branchName(),
                from,
                to,
                report.grossSalesMinorUnits(),
                report.refundTotalMinorUnits(),
                report.netSalesMinorUnits(),
                approvedExpenses,
                report.netSalesMinorUnits() - approvedExpenses);
    }

    /**
     * Gap-analysis #14 (Section 11 💡): Kasa ekranında gösterilebilecek küçük bir ciro
     * özeti - ayrı bir permission'la (REPORT_FINANCIAL_SUMMARY_VIEW) korunur, düz
     * REPORT_VIEW'dan bağımsız, böylece işletme bunu CASHIER'a açmadan
     * BRANCH_MANAGER/BUSINESS_ADMIN'e gösterebilir. Endpoint yolu (/kitchen-summary) ve
     * DTO adı, ayrı bir Mutfak/KDS ekranı kaldırıldıktan sonra da bilinçli olarak
     * değiştirilmedi - çalışan, test edilmiş bir URL'yi kozmetik nedenle yeniden
     * adlandırmak gereksiz churn olurdu (bkz. RefundController'ın aynı gerekçesi).
     */
    @GetMapping({"/api/staff/reports/kitchen-summary", "/api/staff/branches/{branchId}/reports/kitchen-summary"})
    public KitchenFinancialSummaryResponse kitchenFinancialSummary(
            @PathVariable(required = false) UUID branchId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = resolveReportContext(sessionCookie, Permission.REPORT_FINANCIAL_SUMMARY_VIEW, branchId);
        branchId = context.activeBranchId();
        BranchSalesReportView report = reportingService.getBranchReport(context.businessId(), branchId, from, to);
        return new KitchenFinancialSummaryResponse(
                branchId, from, to, report.grossSalesMinorUnits(), report.netSalesMinorUnits(), report.orderCount());
    }

    /** Section 13.2: BUSINESS_ADMIN'in zincir görünümü - tüm şubeler için karşılaştırmalı ciro/refund/sipariş. */
    @GetMapping("/api/staff/reports/chain")
    public ChainSalesReportResponse chainReport(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContext(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.REPORT_CHAIN_VIEW);
        return toResponse(reportingService.getChainReport(context.businessId(), from, to));
    }

    private StaffContext resolveReportContext(String sessionCookie, Permission permission, UUID requestedBranchId) {
        UUID sessionId = StaffCookieSupport.parseSessionId(sessionCookie);
        return requestedBranchId == null
                ? staffAuthService.resolveStaffContextForActiveBranch(sessionId, permission)
                : staffAuthService.resolveStaffContextForBranch(sessionId, permission, requestedBranchId);
    }

    private static BranchSalesReportResponse toResponse(BranchSalesReportView view) {
        return new BranchSalesReportResponse(
                view.branchId(),
                view.branchName(),
                view.from(),
                view.to(),
                view.grossSalesMinorUnits(),
                view.netSalesMinorUnits(),
                view.refundTotalMinorUnits(),
                view.orderCount(),
                view.acceptedOrderCount(),
                view.rejectedOrderCount(),
                view.averageOrderValueMinorUnits(),
                view.tableVisitCount(),
                view.guestCountTotal(),
                view.guestCountRecordedVisitCount(),
                view.averagePreparationSeconds(),
                view.completedOrderCount(),
                view.productBreakdown().stream().map(StaffReportingController::toResponse).toList(),
                view.categoryBreakdown().stream().map(StaffReportingController::toResponse).toList(),
                view.hourlyDistribution().stream().map(StaffReportingController::toResponse).toList());
    }

    private static ChainSalesReportResponse toResponse(ChainSalesReportView view) {
        return new ChainSalesReportResponse(
                view.businessId(),
                view.from(),
                view.to(),
                view.totalGrossSalesMinorUnits(),
                view.totalNetSalesMinorUnits(),
                view.totalRefundMinorUnits(),
                view.totalOrderCount(),
                view.branches().stream().map(StaffReportingController::toResponse).toList());
    }

    private static ProductSalesResponse toResponse(ProductSalesView view) {
        return new ProductSalesResponse(view.productId(), view.productName(), view.quantitySold(), view.revenueMinorUnits());
    }

    private static CategorySalesResponse toResponse(CategorySalesView view) {
        return new CategorySalesResponse(view.categoryId(), view.categoryName(), view.revenueMinorUnits());
    }

    private static HourlySalesResponse toResponse(HourlySalesView view) {
        return new HourlySalesResponse(view.hourOfDay(), view.orderCount(), view.revenueMinorUnits());
    }
}
