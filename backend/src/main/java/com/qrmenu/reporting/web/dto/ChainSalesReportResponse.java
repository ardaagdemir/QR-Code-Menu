package com.qrmenu.reporting.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ChainSalesReportResponse(
        UUID businessId,
        LocalDate from,
        LocalDate to,
        long totalGrossSalesMinorUnits,
        long totalNetSalesMinorUnits,
        long totalRefundMinorUnits,
        int totalOrderCount,
        List<BranchSalesReportResponse> branches) {
}
