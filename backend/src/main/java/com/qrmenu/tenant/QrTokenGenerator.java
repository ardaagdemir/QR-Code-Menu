package com.qrmenu.tenant;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Opaque, high-entropy random token - no JWT/claims scheme (Section 12: over-engineered
 * for a value that only needs to be looked up and compared, never decoded).
 */
final class QrTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 24;

    private QrTokenGenerator() {
    }

    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
