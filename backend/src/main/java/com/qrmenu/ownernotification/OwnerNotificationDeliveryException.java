package com.qrmenu.ownernotification;

/** Wraps any adapter-level delivery failure (e.g. SMTP connect/send error) with a message safe to persist. */
public class OwnerNotificationDeliveryException extends RuntimeException {

    public OwnerNotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
