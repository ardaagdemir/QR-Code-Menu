package com.qrmenu.payment;

import com.qrmenu.payment.repository.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Section 6/9: "PROCESSING -> EXPIRED: timeout" - a payment that never received a
 * webhook (real provider outage, or the mock flow's customer never calling
 * /mock-outcome, e.g. they closed the tab mid-checkout) would otherwise sit in
 * PROCESSING forever, silently blocking that Order from ever being retried. Mirrors
 * OrderCleanupScheduler's shape (fixedDelay poll over a createdAt cutoff). 15 minutes
 * is a 💡 RECOMMENDED default, not a fixed product decision (Section 1.2, item 11) -
 * a real payment session should resolve in seconds to low minutes, so this is a
 * generous upper bound, not a UX target. Each payment is expired via
 * {@link PaymentTimeoutExpirer} and isolated with a try/catch here - one bad payment
 * (e.g. its Order already gone) is logged and skipped instead of rolling back the
 * markExpired()+markOrderPaymentFailed() already committed for earlier payments in the
 * same poll cycle, or blocking the rest of the batch from being expired.
 */
@Component
class PaymentTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutScheduler.class);

    static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(15);

    private final PaymentRepository paymentRepository;
    private final PaymentTimeoutExpirer paymentTimeoutExpirer;

    PaymentTimeoutScheduler(PaymentRepository paymentRepository, PaymentTimeoutExpirer paymentTimeoutExpirer) {
        this.paymentRepository = paymentRepository;
        this.paymentTimeoutExpirer = paymentTimeoutExpirer;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    void expireStaleProcessingPayments() {
        Instant cutoff = Instant.now().minus(PROCESSING_TIMEOUT);
        List<Payment> stalePayments = paymentRepository.findAllByStatusAndCreatedAtBefore(PaymentStatus.PROCESSING, cutoff);
        for (Payment payment : stalePayments) {
            try {
                paymentTimeoutExpirer.expirePayment(payment.getId());
            } catch (Exception e) {
                log.error("Failed to expire stale processing payment {}; skipping to the next payment", payment.getId(), e);
            }
        }
    }
}
