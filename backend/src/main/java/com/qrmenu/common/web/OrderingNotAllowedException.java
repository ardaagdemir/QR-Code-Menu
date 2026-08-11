package com.qrmenu.common.web;

/**
 * Thrown by the authoritative, pre-payment ordering-allowed check (Section 9,
 * Milestone 5: "Branch ordering-enabled/çalışma saati kontrolünün 'kesin/otoriter'
 * hali") - the branch exists and is a valid target, but isn't currently accepting
 * orders (disabled, or outside its configured opening hours). A business-state
 * conflict, not a lookup failure - same reasoning as ProductNotOrderableException.
 */
public class OrderingNotAllowedException extends RuntimeException {

    public OrderingNotAllowedException(String message) {
        super(message);
    }
}
