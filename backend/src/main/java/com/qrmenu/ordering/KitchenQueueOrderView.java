package com.qrmenu.ordering;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One IN_KITCHEN/AWAITING_STORE_ACCEPTANCE order plus its items/options, as the kitchen
 * and order-control modules need to render a KDS/kasa order card. tableLabel is nullable -
 * resolved best-effort from the order's TableVisit/RestaurantTable chain (see
 * OrderingService.buildKitchenQueueView), null if that chain can't be resolved.
 */
public record KitchenQueueOrderView(
        CustomerOrder order, List<OrderItem> items, Map<UUID, List<OrderItemOption>> optionsByItemId, String tableLabel) {
}
