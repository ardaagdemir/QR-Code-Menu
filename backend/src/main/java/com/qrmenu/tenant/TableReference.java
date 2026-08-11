package com.qrmenu.tenant;

import java.util.UUID;

/**
 * Public cross-module contract: what the customer-session module (and, later, ordering)
 * needs to know about a table once a QR token has been resolved. Deliberately not the
 * RestaurantTable entity itself, so other modules never touch tenant's repositories
 * (Section 2, enforced by ModuleBoundaryTest).
 */
public record TableReference(
        UUID businessId,
        UUID branchId,
        UUID tableId,
        String businessName,
        String branchName,
        String tableLabel) {
}
