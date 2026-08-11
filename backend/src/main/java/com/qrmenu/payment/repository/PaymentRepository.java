package com.qrmenu.payment.repository;

import com.qrmenu.payment.Payment;
import com.qrmenu.payment.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderPaymentIntentId(String provider, String providerPaymentIntentId);

    Optional<Payment> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    /** PaymentTimeoutScheduler (Milestone 9): PROCESSING payments that never received a webhook. */
    List<Payment> findAllByStatusAndCreatedAtBefore(PaymentStatus status, Instant cutoff);
}
