package com.qrmenu.ownernotification;

/**
 * Section 15: "Bildirim altyapısı provider bağımsız olmalıdır." {@link
 * EmailOwnerNotificationAdapter} is the only implementation today; a future WhatsApp adapter
 * plugs in behind the same interface without touching {@link OwnerNotificationService}.
 */
public interface OwnerNotificationPort {

    OwnerNotificationChannel channel();

    /** Throws {@link OwnerNotificationDeliveryException} on failure - the caller logs it and moves to the next recipient. */
    void send(OwnerNotificationMessage message);
}
