package com.qrmenu.common.web;

/**
 * Thrown when a table label collides with an existing one within the same branch -
 * surfaces from the DB's uq_restaurant_table_branch_label index racing a concurrent
 * request (see TenantService.bulkCreateTables, which locks the branch row to make this
 * rare, but a concurrent single-table create can still land on the same label).
 */
public class DuplicateTableLabelException extends RuntimeException {

    public DuplicateTableLabelException(String message) {
        super(message);
    }
}
