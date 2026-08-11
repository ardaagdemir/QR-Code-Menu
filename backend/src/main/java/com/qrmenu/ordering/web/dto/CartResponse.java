package com.qrmenu.ordering.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * orderTrackingToken is non-null exactly once: on the response to the call that creates
 * the DRAFT order (Section 2 - the raw token is returned once, never re-exposed on
 * subsequent GET/removal calls).
 */
public record CartResponse(
        UUID tableVisitId,
        UUID orderId,
        String status,
        long totalMinorUnits,
        List<CartItemResponse> items,
        String orderTrackingToken) {

    public static CartResponse empty(UUID tableVisitId) {
        return new CartResponse(tableVisitId, null, null, 0L, List.of(), null);
    }
}
