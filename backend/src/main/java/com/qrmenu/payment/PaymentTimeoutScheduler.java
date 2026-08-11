package com.qrmenu.payment;

import com.qrmenu.ordering.OrderingService;
import com.qrmenu.payment.repository.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Section 6/9: "PROCESSING -> EXPIRED: timeout" - a payment that never received a
 * webhook (real provider outage, or the mock flow's customer never calling
 * /mock-outcome, e.g. they closed the tab mid-checkout) would otherwise sit in
 * PROCESSING forever, silently blocking that Order from ever being retried. Mirrors
 * OrderCleanupScheduler's shape (fixedDelay poll over a createdAt cutoff). 15 minutes
 * is a 💡 RECOMMENDED default, not a fixed product decision (Section 1.2, item 11) -
 * a real payment session should resolve in seconds to low minutes, so this is a
 * generous upper bound, not a UX target.
 */
@Component
class PaymentTimeoutScheduler {

    static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(15);

    private final PaymentRepository paymentRepository;
    private final OrderingService orderingService;

    PaymentTimeoutScheduler(PaymentRepository paymentRepository, OrderingService orderingService) {
        this.paymentRepository = paymentRepository;
        this.orderingService = orderingService;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    void expireStaleProcessingPayments() {
        Instant cutoff = Instant.now().minus(PROCESSING_TIMEOUT);
        List<Payment> stalePayments = paymentRepository.findAllByStatusAndCreatedAtBefore(PaymentStatus.PROCESSING, cutoff);
        for (Payment payment : stalePayments) {
            payment.markExpired();
            paymentRepository.save(payment);
            // Same reaction as a FAILED webhook (PaymentWebhookService) - the order
            // returns to PAYMENT_FAILED so the customer can start a new payment attempt.
            orderingService.markOrderPaymentFailed(payment.getOrderId());
        }
    }
}
