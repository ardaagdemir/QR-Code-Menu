package com.qrmenu.refund.web.dto;

import java.time.Instant;
import java.util.List;

public record ReceiptResponse(
        String businessName,
        String branchName,
        Integer orderNumber,
        Instant orderCreatedAt,
        List<ReceiptItemResponse> items,
        long totalMinorUnits,
        long totalRefundedMinorUnits,
        long netPaidMinorUnits,
        List<RefundResponse> refunds) {
}
