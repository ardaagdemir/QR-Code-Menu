package com.qrmenu.ordering;

import java.util.List;

/**
 * Section 5: the read-only, dar/narrow view `GET /order/track/{token}` returns -
 * status/number/summary only, never anything that would let the caller change,
 * cancel, or refund the order.
 */
public record OrderTrackingView(CustomerOrder order, List<OrderItem> items) {
}
