package com.qrmenu.common.web;

/**
 * Thrown when a payment webhook's signature does not verify against the configured
 * provider secret (Section 1.3: signature verification is mandatory, even for the mock
 * adapter, and uses the same code path a real provider integration would). Surfaces as
 * 401 - the caller is not who it claims to be, distinct from a malformed/unknown event
 * (which would be a 400/404 instead).
 */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
