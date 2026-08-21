package com.qrmenu.reporting.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Section 17: gross sales, refunds, net sales, recorded expenses and net operating result.
 * Deliberately not called "net profit/kâr" anywhere (backend or frontend) - Section 17's
 * warning that tax/COGS/payroll accrual/depreciation aren't modeled makes a legal-profit
 * claim misleading; the UI must label this "Yönetimsel Net Sonuç" (Section 17, 💡 note).
 */
public record OperatingResultResponse(
        UUID branchId,
        String branchName,
        LocalDate from,
        LocalDate to,
        long grossSalesMinorUnits,
        long refundTotalMinorUnits,
        long netSalesMinorUnits,
        long manualExpensesMinorUnits,
        long recurringExpensesMinorUnits,
        long totalExpensesMinorUnits,
        long netOperatingResultMinorUnits) {
}
