package com.qrmenu.dailyclose.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyCloseReportResponse(
        UUID id,
        UUID branchId,
        String branchName,
        LocalDate businessDate,
        Instant periodStart,
        Instant periodEnd,
        long grossSalesMinorUnits,
        long refundTotalMinorUnits,
        long netSalesMinorUnits,
        int orderCount,
        int acceptedOrderCount,
        int rejectedOrderCount,
        long averageOrderValueMinorUnits,
        long tableVisitCount,
        String status,
        Instant generatedAt) {
}
