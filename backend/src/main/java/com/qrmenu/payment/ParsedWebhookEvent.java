package com.qrmenu.payment;

/** Result of PaymentProviderPort.parseWebhookEvent - provider-agnostic shape PaymentWebhookService acts on. */
public record ParsedWebhookEvent(String eventId, String providerPaymentIntentId, WebhookOutcome outcome) {
}
