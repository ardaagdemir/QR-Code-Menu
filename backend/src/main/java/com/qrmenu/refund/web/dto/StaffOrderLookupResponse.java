package com.qrmenu.refund.web.dto;

import java.util.List;
import java.util.UUID;

public record StaffOrderLookupResponse(
        UUID orderId,
        Integer orderNumber,
        String status,
        long totalMinorUnits,
        List<StaffOrderItemResponse> items,
        List<RefundResponse> refunds) {
}
