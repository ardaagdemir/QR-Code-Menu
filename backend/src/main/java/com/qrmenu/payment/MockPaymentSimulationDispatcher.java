package com.qrmenu.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The "ayrı, asenkron bir mock webhook çağrısı" from Section 2: runs on a separate
 * thread from the request that triggered it (MockPaymentSimulationService), so the
 * customer's "simulate success" click returns immediately while this call - which is
 * the ONLY thing that can actually finalize the payment - happens moments later,
 * through the exact same signature+idempotency code path
 * (PaymentWebhookService.handleIncomingWebhook) a real provider's server-to-server
 * callback would use. A separate bean (not a method called via `this.` on
 * MockPaymentSimulationService) because @Async only intercepts calls that go through
 * the Spring proxy - a self-invocation would silently run synchronously.
 */
@Component
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "mock", matchIfMissing = true)
class MockPaymentSimulationDispatcher {

    private final MockPaymentProviderAdapter mockAdapter;
    private final PaymentWebhookService paymentWebhookService;

    MockPaymentSimulationDispatcher(MockPaymentProviderAdapter mockAdapter, PaymentWebhookService paymentWebhookService) {
        this.mockAdapter = mockAdapter;
        this.paymentWebhookService = paymentWebhookService;
    }

    @Async
    void dispatch(String providerPaymentIntentId, WebhookOutcome outcome) {
        SignedWebhookPayload signed = mockAdapter.buildSignedWebhookPayload(providerPaymentIntentId, outcome);
        paymentWebhookService.handleIncomingWebhook(signed.rawBody(), signed.signature());
    }
}
