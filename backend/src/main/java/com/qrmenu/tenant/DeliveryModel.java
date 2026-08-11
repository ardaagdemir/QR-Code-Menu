package com.qrmenu.tenant;

/**
 * Section 4/9, Milestone 9: branch-level delivery model. CUSTOMER_PICKUP shows the
 * branch's pickup board (order numbers of READY orders, kiosk screen); WAITER_DELIVERY
 * is the model every branch implicitly used before this milestone (staff brings the
 * order to the table, no pickup board).
 */
public enum DeliveryModel {
    CUSTOMER_PICKUP,
    WAITER_DELIVERY
}
