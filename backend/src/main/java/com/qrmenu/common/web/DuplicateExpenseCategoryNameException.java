package com.qrmenu.common.web;

/**
 * Thrown when creating or renaming an ExpenseCategory would collide with an existing
 * category's name within the same business, case-insensitively - whether caught by the
 * app-layer pre-check or surfaced from the DB's uq_expense_category_business_name_lower
 * index racing a concurrent request.
 */
public class DuplicateExpenseCategoryNameException extends RuntimeException {

    public DuplicateExpenseCategoryNameException(String message) {
        super(message);
    }
}
