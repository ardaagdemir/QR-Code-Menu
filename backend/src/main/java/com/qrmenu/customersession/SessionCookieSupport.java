package com.qrmenu.customersession;

import java.util.UUID;

/**
 * The AnonymousCustomerSession cookie's name/parsing, shared by every public,
 * cookie-authenticated endpoint (QR check-in, cart) - both in this module and in
 * cross-module callers (e.g. ordering's CartController) that need to identify the
 * caller's session without reaching into this module's repositories.
 */
public final class SessionCookieSupport {

    public static final String COOKIE_NAME = "qrmenu_session";

    private SessionCookieSupport() {
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
