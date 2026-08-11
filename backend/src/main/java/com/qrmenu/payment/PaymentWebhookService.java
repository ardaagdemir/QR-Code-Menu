package com.qrmenu.payment;

import com.qrmenu.common.web.InvalidWebhookSignatureException;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.payment.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single code path every payment webhook delivery goes through, regardless of
 * where it originated - a real HTTP call from a provider (PaymentWebhookController) or
 * the mock adapter's async simulated callback (MockPaymentSimulationDispatcher).
 * Section 1.3: "Mock adapter da AYNI imza doğrulama ve event-idempotency kod yolundan
 * geçer" - this class IS that shared code path, not a mock-specific shortcut.
 */
@Service
public class PaymentWebhookService {

    private final PaymentProviderPort paymentProvider;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookIdempotencyGuard idempotencyGuard;
    private final OrderingService orderingService;

    public PaymentWebhookService(
            PaymentProviderPort paymentProvider,
            PaymentRepository paymentRepository,
            PaymentWebhookIdempotencyGuard idempotencyGuard,
            OrderingService orderingService) {
        this.paymentProvider = paymentProvider;
        this.paymentRepository = paymentRepository;
        this.idempotencyGuard = idempotencyGuard;
        this.orderingService = orderingService;
    }

    /**
     * rawBody must be the exact, unparsed request body - signature verification runs
     * against it before any JSON deserialization (Section 1.3).
     */
    @Transactional
    public void handleIncomingWebhook(String rawBody, String signatureHeader) {
        if (!paymentProvider.verifyWebhookSignature(rawBody, signatureHeader)) {
            throw new InvalidWebhookSignatureException("Webhook signature verification failed");
        }
        ParsedWebhookEvent event = paymentProvider.parseWebhookEvent(rawBody);

        Payment payment = paymentRepository
                .findByProviderAndProviderPaymentIntentId(paymentProvider.providerName(), event.providerPaymentIntentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment found for provider intent: " + event.providerPaymentIntentId()));

        try {
            idempotencyGuard.recordOrThrowIfDuplicate(paymentProvider.providerName(), event.eventId(), payment.getId());
        } catch (DataIntegrityViolationException duplicateDelivery) {
            // Section 1.3: "(provider, event_id) üzerinde DB unique constraint" - a
            // network-retried delivery of an event we've already processed is a no-op,
            // not an error. The guard's own REQUIRES_NEW transaction already rolled
            // back cleanly (see its Javadoc) - this transaction is unaffected.
            return;
        }

        // Extra safety net beyond event-id idempotency (Section 1.3: "sipariş zaten
        // PAID ise no-op") - a payment already in a terminal state is left alone. EXPIRED
        // (Milestone 9's PaymentTimeoutScheduler) counts as terminal too: a webhook that
        // arrives after the timeout window already lost the race and the order has
        // moved on (PAYMENT_FAILED, retriable) - applying it now would either be a very
        // late false success or crash on Payment's PROCESSING-only state guards.
        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.EXPIRED) {
            return;
        }

        if (event.outcome() == WebhookOutcome.SUCCEEDED) {
            payment.markSucceeded();
            paymentRepository.save(payment);
            orderingService.markOrderPaid(payment.getOrderId());
        } else {
            payment.markFailed();
            paymentRepository.save(payment);
            orderingService.markOrderPaymentFailed(payment.getOrderId());
        }
    }
}
