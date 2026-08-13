package com.qrmenu.reporting.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BranchSalesReportResponse(
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
        List<ProductSalesResponse> productBreakdown,
        List<CategorySalesResponse> categoryBreakdown,
        List<HourlySalesResponse> hourlyDistribution) {
}
