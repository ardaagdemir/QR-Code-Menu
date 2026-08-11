package com.qrmenu.payment;

/** JSON shape of the mock provider's webhook body - deliberately shaped like a plausible real-provider event. */
record MockWebhookPayload(String eventId, String providerPaymentIntentId, String outcome) {
}
