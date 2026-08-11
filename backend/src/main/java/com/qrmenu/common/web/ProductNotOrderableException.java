package com.qrmenu.common.web;

/**
 * Thrown when a product exists but cannot currently be added to a cart - no
 * BranchProduct row for the branch (opt-in - Section 5) or an UNAVAILABLE one. Distinct
 * from ResourceNotFoundException (404): the product id itself is valid and public
 * (visible in the menu), this is a business-state conflict, not a lookup failure.
 */
public class ProductNotOrderableException extends RuntimeException {

    public ProductNotOrderableException(String message) {
        super(message);
    }
}
