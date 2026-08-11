package com.qrmenu.ordering.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Section 5: intentionally narrow - status/number/summary only, nothing that would let
 * the holder of a leaked token change, cancel, or refund the order. Gap-analysis #6:
 * latestRefundStatus surfaces "refund başlatıldı/tamamlandı/başarısız" (Section 9) -
 * the most recently created Refund's status, null if the order has none yet.
 */
public record OrderTrackingResponse(
        UUID orderId,
        Integer orderNumber,
        String status,
        long totalMinorUnits,
        String deliveryModel,
        String latestRefundStatus,
        List<OrderTrackingItemResponse> items) {
}
