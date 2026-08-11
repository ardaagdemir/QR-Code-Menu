package com.qrmenu.staffaccess;

import java.util.UUID;

/** Mirrors customersession.SessionCookieSupport - the qrmenu_staff_session cookie's name/parsing. */
public final class StaffCookieSupport {

    public static final String COOKIE_NAME = "qrmenu_staff_session";

    private StaffCookieSupport() {
    }

    public static UUID parseSessionId(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(cookieValue);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
