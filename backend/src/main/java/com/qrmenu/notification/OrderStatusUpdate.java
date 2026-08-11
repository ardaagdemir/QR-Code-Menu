package com.qrmenu.notification;

import java.util.UUID;

/** Channel-agnostic payload for an order status change - what any OrderStatusNotifier implementation sends. */
public record OrderStatusUpdate(UUID orderId, UUID branchId, String orderStatus, Integer orderNumber) {
}
