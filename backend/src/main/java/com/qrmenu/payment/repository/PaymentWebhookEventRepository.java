package com.qrmenu.payment.repository;

import com.qrmenu.payment.PaymentWebhookEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
}
