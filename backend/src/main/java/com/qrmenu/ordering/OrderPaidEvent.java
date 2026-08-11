package com.qrmenu.ordering;

import java.util.UUID;

/**
 * Outbox payload written when an Order transitions to PAID (Section 2, mock payment
 * flow step 6: "Order PAID olur ve outbox event yazılır"). No subscriber exists yet -
 * the first one (kitchen, reacting to this to drop the order into IN_KITCHEN) lands in
 * Milestone 6.
 */
public record OrderPaidEvent(UUID orderId, UUID businessId, UUID branchId, UUID tableVisitId, long totalMinorUnits) {
}
