package com.qrmenu.common.web;

/**
 * Thrown when a PLATFORM_ADMIN tries to deactivate a branch that still has an order in
 * one of OrderingService's operationally-active statuses - deactivating would strand
 * that order mid-flight (no more staff access, no more customer ordering/payment for
 * the branch it belongs to), so the deactivation is rejected outright instead.
 */
public class BranchHasActiveOrdersException extends RuntimeException {

    public BranchHasActiveOrdersException(String message) {
        super(message);
    }
}
