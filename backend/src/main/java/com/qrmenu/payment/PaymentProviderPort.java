package com.qrmenu.payment;

/**
 * Section 2 (CONFIRMED): a provider-agnostic seam so switching from the mock adapter to
 * a real provider (Stripe/iyzico/PayTR/... - name still OPEN, Section 1.2 item 1) only
 * requires a new adapter, never a change to PaymentService/PaymentWebhookService. The
 * concrete implementation is selected via the `payment.provider` config key.
 */
public interface PaymentProviderPort {

    String providerName();

    ProviderPaymentIntent createPaymentIntent(long amountMinorUnits);

    /**
     * Verifies rawBody (the exact, unparsed webhook request body) against
     * signatureHeader. Must be called before the body is ever deserialized (Section
     * 1.3: a security filter/body parser must not consume the body ahead of this).
     */
    boolean verifyWebhookSignature(String rawBody, String signatureHeader);

    ParsedWebhookEvent parseWebhookEvent(String rawBody);

    /** Not implemented by the mock adapter yet - the refund module lands in Milestone 7. */
    void refund(String providerPaymentIntentId, long amountMinorUnits);
}
