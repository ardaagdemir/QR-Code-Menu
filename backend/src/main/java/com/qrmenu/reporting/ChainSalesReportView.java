package com.qrmenu.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Gap-analysis #8 (Section 13.2): BUSINESS_ADMIN's zincir görünümü - per-branch reports plus business-wide totals. */
public record ChainSalesReportView(
        UUID businessId,
        LocalDate from,
        LocalDate to,
        long totalGrossSalesMinorUnits,
        long totalNetSalesMinorUnits,
        long totalRefundMinorUnits,
        int totalOrderCount,
        List<BranchSalesReportView> branches) {
}
