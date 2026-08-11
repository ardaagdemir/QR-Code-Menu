package com.qrmenu.ordering;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * orderTrackingToken (Section 2, 5): a cryptographically random, opaque token - not
 * derived from the (predictable) order number, not a JWT/claims scheme (Section 12).
 * Only the SHA-256 hash is ever persisted (CustomerOrder.trackingTokenHash); the raw
 * value returned to the caller once, at Order-creation time, is never stored. SHA-256 is
 * sufficient here (unlike a password hash) because the token itself is already
 * high-entropy random data, not a low-entropy secret guessable by brute force (Section 2).
 */
final class OrderTrackingTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private OrderTrackingTokenGenerator() {
    }

    static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
