package com.qrmenu.common.web;

/**
 * Thrown when staff tries to archive a RestaurantTable that still has an active
 * (open, unexpired) TableVisit or an order in one of OrderingService's
 * operationally-active statuses - archiving would strand that visit/order mid-flight
 * (no more QR check-in, table treated as out of service), same reasoning as
 * BranchHasActiveOrdersException. See OrderingService.archiveTable.
 */
public class TableInUseException extends RuntimeException {

    public TableInUseException(String message) {
        super(message);
    }
}
