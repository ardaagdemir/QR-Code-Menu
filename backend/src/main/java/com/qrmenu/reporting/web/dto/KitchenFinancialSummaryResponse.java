package com.qrmenu.reporting.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gap-analysis #14 (product-requirements.md Section 11 💡): "mutfak ekranında ciro/finansal
 * veri gösterimi role sabitlenmez; REPORT_FINANCIAL_SUMMARY_VIEW permission'ı olan
 * kullanıcıya gösterilir." A deliberately small subset of BranchSalesReportView - just
 * enough for a KDS header stat block, not the full report.
 */
public record KitchenFinancialSummaryResponse(
        UUID branchId, LocalDate from, LocalDate to, long grossSalesMinorUnits, long netSalesMinorUnits, int orderCount) {
}
