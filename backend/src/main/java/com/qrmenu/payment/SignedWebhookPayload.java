package com.qrmenu.payment;

/** A raw webhook body paired with its signature - what a real provider's server-to-server callback carries. */
record SignedWebhookPayload(String rawBody, String signature) {
}
