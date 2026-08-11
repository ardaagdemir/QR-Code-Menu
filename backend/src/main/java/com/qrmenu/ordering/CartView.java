package com.qrmenu.ordering;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * newlyIssuedTrackingToken is only non-null on the request that just created the Order
 * (Section 2: the raw orderTrackingToken is returned exactly once, never again on
 * subsequent reads) - see CartController.
 */
public record CartView(
        CustomerOrder order,
        List<OrderItem> items,
        Map<UUID, List<OrderItemOption>> optionsByItemId,
        String newlyIssuedTrackingToken) {
}
