package com.qrmenu.common.web;

/** Thrown when a staff-facing endpoint has no valid qrmenu_staff_session cookie (missing, unknown, or expired). */
public class StaffAuthenticationRequiredException extends RuntimeException {

    public StaffAuthenticationRequiredException(String message) {
        super(message);
    }
}
