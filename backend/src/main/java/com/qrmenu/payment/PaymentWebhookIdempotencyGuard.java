package com.qrmenu.payment;

import com.qrmenu.payment.repository.PaymentWebhookEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates the (provider, event_id) uniqueness check in its own physical transaction
 * (REQUIRES_NEW - a separate connection/Hibernate Session). A constraint-violation
 * flush failure leaves the Hibernate transaction it happened in marked
 * rollback-only internally, even if the DataIntegrityViolationException is caught in
 * application code - attempting to keep going and later commit that same transaction
 * throws UnexpectedRollbackException. Running the insert attempt in its own
 * transaction means only this transaction pays that cost; PaymentWebhookService's
 * outer transaction (payment/order state changes) is unaffected by a duplicate
 * delivery. The exception is deliberately NOT caught here - it must propagate out of
 * this @Transactional method so Spring's proxy rolls this transaction back correctly,
 * rather than attempting a doomed commit.
 */
@Component
class PaymentWebhookIdempotencyGuard {

    private final PaymentWebhookEventRepository repository;

    PaymentWebhookIdempotencyGuard(PaymentWebhookEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordOrThrowIfDuplicate(String provider, String eventId, UUID paymentId) {
        repository.saveAndFlush(new PaymentWebhookEvent(provider, eventId, paymentId));
    }
}
