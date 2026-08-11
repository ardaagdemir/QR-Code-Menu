package com.qrmenu.ownernotification;

/** Channel-agnostic content the {@link OwnerNotificationPort} adapter renders/delivers. */
public record OwnerNotificationMessage(String recipientEmail, String subject, String body) {
}
