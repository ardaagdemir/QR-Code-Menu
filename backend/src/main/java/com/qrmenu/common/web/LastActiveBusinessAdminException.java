package com.qrmenu.common.web;

/**
 * Thrown when deactivating a BUSINESS_ADMIN would leave a business with zero active
 * BUSINESS_ADMIN staff - every business must always retain at least one, or nobody would be
 * left able to manage its staff, menu, or branches.
 */
public class LastActiveBusinessAdminException extends RuntimeException {

    public LastActiveBusinessAdminException(String message) {
        super(message);
    }
}
