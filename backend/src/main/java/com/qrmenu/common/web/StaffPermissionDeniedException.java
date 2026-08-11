package com.qrmenu.common.web;

/**
 * Thrown when an authenticated staff session lacks the required Permission or branch
 * scope for the action - distinct from StaffAuthenticationRequiredException (401): the
 * caller IS who they say they are, they're just not allowed to do this.
 */
public class StaffPermissionDeniedException extends RuntimeException {

    public StaffPermissionDeniedException(String message) {
        super(message);
    }
}
