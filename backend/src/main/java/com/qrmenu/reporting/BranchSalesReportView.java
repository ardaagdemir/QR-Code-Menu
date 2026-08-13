package com.qrmenu.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gap-analysis #8 (product-requirements.md Section 13.1): one branch's sales report for
 * a selectable [from, to] business-date range (inclusive, resolved in the branch's own
 * timezone). grossSales is the sum of paid order totals (Section 13.4: derived from the
 * immutable Order/OrderItem snapshot, not live Product prices); netSales subtracts
 * completed refunds. averageOrderValue is grossSales / orderCount (0 when orderCount is
 * 0). Gap-analysis #17 (Section 13.3): guestCountTotal/guestCountRecordedVisitCount are
 * deliberately separate from tableVisitCount, not derived from it - they only sum visits
 * where a real headcount was actually entered, never defaulting an unset visit to 1.
 */
public record BranchSalesReportView(
        UUID branchId,
        String branchName,
        LocalDate from,
        LocalDate to,
        long grossSalesMinorUnits,
        long netSalesMinorUnits,
        long refundTotalMinorUnits,
        int orderCount,
        int acceptedOrderCount,
        int rejectedOrderCount,
        long averageOrderValueMinorUnits,
        long tableVisitCount,
        long guestCountTotal,
        long guestCountRecordedVisitCount,
        List<ProductSalesView> productBreakdown,
        List<CategorySalesView> categoryBreakdown,
        List<HourlySalesView> hourlyDistribution) {
}
