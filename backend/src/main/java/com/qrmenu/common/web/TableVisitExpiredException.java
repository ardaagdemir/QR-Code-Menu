package com.qrmenu.common.web;

/**
 * Thrown by the authoritative, synchronous check that gates order creation/advancement
 * (customer TableVisit security hardening): the visit is owned by the caller and not
 * yet closed by the async cleanup scheduler, but has independently passed its 60-minute
 * inactivity timeout or its 4-hour absolute lifetime. Distinct from ResourceNotFoundException
 * (404, "gone"): the visit still exists and its menu/order-history reads keep working -
 * only starting/advancing a new order is rejected, so this maps to 410 Gone rather than
 * 404 or the 409 already used by ProductNotOrderableException/OrderingNotAllowedException.
 */
public class TableVisitExpiredException extends RuntimeException {

    public TableVisitExpiredException(String message) {
        super(message);
    }
}
