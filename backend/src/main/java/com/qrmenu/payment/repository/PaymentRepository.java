package com.qrmenu.payment.repository;

import com.qrmenu.payment.Payment;
import com.qrmenu.payment.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderPaymentIntentId(String provider, String providerPaymentIntentId);

    Optional<Payment> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    /** Defense-in-depth for the paid-amount invariant when concurrent refunds arrive. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderId = :orderId and p.status = :status")
    Optional<Payment> findByOrderIdAndStatusForUpdate(@Param("orderId") UUID orderId, @Param("status") PaymentStatus status);

    /** PaymentTimeoutScheduler (Milestone 9): PROCESSING payments that never received a webhook. */
    List<Payment> findAllByStatusAndCreatedAtBefore(PaymentStatus status, Instant cutoff);
}
