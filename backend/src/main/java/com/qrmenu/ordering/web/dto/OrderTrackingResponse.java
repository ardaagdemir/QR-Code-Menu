package com.qrmenu.ordering.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Section 5: intentionally narrow - status/number/summary only, nothing that would let
 * the holder of a leaked token change, cancel, or refund the order.
 */
public record OrderTrackingResponse(
        UUID orderId,
        Integer orderNumber,
        String status,
        long totalMinorUnits,
        String deliveryModel,
        List<OrderTrackingItemResponse> items) {
}
