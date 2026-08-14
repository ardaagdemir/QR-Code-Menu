package com.qrmenu.payment;

import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.payment.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires exactly one stale PROCESSING payment per call, in its own REQUIRES_NEW
 * transaction (same reasoning as PaymentWebhookIdempotencyGuard) - a separate bean, not
 * a method called via `this.` on PaymentTimeoutScheduler, since self-invocation bypasses
 * the Spring proxy entirely and @Transactional would silently not apply. REQUIRES_NEW
 * (not just a plain @Transactional) matters here specifically: if this payment's
 * markOrderPaymentFailed() fails (e.g. its Order already in an unexpected state), the
 * proxy must roll back a transaction it physically owns, undoing markExpired()+save()
 * too - joining an ambient transaction would only mark it rollback-only, leaving that
 * half-applied write sitting there uncommitted until whatever started the ambient
 * transaction eventually completes. Either way, one payment's failure can't roll back
 * or block payments already processed earlier in the same poll cycle.
 */
@Component
class PaymentTimeoutExpirer {

    private final PaymentRepository paymentRepository;
    private final OrderingService orderingService;

    PaymentTimeoutExpirer(PaymentRepository paymentRepository, OrderingService orderingService) {
        this.paymentRepository = paymentRepository;
        this.orderingService = orderingService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void expirePayment(UUID paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        payment.markExpired();
        paymentRepository.save(payment);
        // Same reaction as a FAILED webhook (PaymentWebhookService) - the order
        // returns to PAYMENT_FAILED so the customer can start a new payment attempt.
        orderingService.markOrderPaymentFailed(payment.getOrderId());
    }
}
