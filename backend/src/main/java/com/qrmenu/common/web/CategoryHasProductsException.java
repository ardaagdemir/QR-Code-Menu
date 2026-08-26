package com.qrmenu.common.web;

/**
 * Thrown when staff tries to delete a MenuCategory that still has at least one Product
 * under it. No silent cascade to the product line - staff must move or delete the
 * products first, same "explicit intermediate step over one-click destructive cascade"
 * reasoning as BranchHasActiveOrdersException.
 */
public class CategoryHasProductsException extends RuntimeException {

    public CategoryHasProductsException(String message) {
        super(message);
    }
}
