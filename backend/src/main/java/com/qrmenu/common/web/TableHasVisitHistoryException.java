package com.qrmenu.common.web;

/**
 * Thrown when staff tries to hard-delete a RestaurantTable that has at least one
 * TableVisit in its history. No silent fallback to archive - the caller must explicitly
 * archive instead (TenantService.archiveLockedTable via OrderingService.archiveTable),
 * same "explicit intermediate step over one-click destructive cascade" reasoning as
 * BranchHasActiveOrdersException/CategoryHasProductsException.
 */
public class TableHasVisitHistoryException extends RuntimeException {

    public TableHasVisitHistoryException(String message) {
        super(message);
    }
}
