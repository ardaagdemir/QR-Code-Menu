package com.qrmenu.common.web;

/**
 * Thrown when a staff user create/email-edit would collide with an existing account's
 * email, case-insensitively - whether caught by the app-layer pre-check or surfaced from
 * the DB's uq_staff_user_email_lower index racing a concurrent request.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
