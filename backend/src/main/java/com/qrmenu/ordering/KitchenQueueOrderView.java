package com.qrmenu.ordering;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One IN_KITCHEN order plus its items/options, as the kitchen module needs to render a KDS order card. */
public record KitchenQueueOrderView(
        CustomerOrder order, List<OrderItem> items, Map<UUID, List<OrderItemOption>> optionsByItemId) {
}
