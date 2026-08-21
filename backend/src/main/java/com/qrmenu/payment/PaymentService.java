package com.qrmenu.payment;

import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.payment.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the payment module's customer-facing operations: starting a
 * payment attempt and reading its status. Everything that actually finalizes a payment
 * (SUCCEEDED/FAILED) goes through PaymentWebhookService instead, never through this
 * class - Section 2: "frontend'in kendisi bu çağrıyı yapmaz."
 */
@Service
public class PaymentService {

    private final OrderingService orderingService;
    private final PaymentRepository paymentRepository;
    private final PaymentProviderPort paymentProvider;

    public PaymentService(OrderingService orderingService, PaymentRepository paymentRepository, PaymentProviderPort paymentProvider) {
        this.orderingService = orderingService;
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
    }

    /**
     * Order DRAFT/PAYMENT_FAILED -> AWAITING_PAYMENT (via OrderingService, which also
     * runs the authoritative ordering-allowed check), then Payment CREATED ->
     * PROCESSING (Section 6: "sağlayıcıya yönlendirildi").
     */
    @Transactional
    public PaymentIntentView createPaymentIntent(UUID tableVisitId, UUID callerSessionId) {
        CustomerOrder order = orderingService.beginPaymentForDraftOrder(tableVisitId, callerSessionId);

        Payment payment =
                paymentRepository.save(new Payment(order.getBusinessId(), order.getId(), paymentProvider.providerName(), order.getTotalMinorUnits()));
        ProviderPaymentIntent providerIntent = paymentProvider.createPaymentIntent(payment.getAmountMinorUnits());
        payment.markProcessing(providerIntent.providerPaymentIntentId());
        payment = paymentRepository.save(payment);

        return toView(payment, order);
    }

    @Transactional(readOnly = true)
    public PaymentStatusView getPaymentStatus(UUID tableVisitId, UUID callerSessionId, UUID paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        CustomerOrder order = orderingService.getOwnedOrder(tableVisitId, callerSessionId, payment.getOrderId());
        return new PaymentStatusView(payment.getId(), order.getId(), payment.getStatus().name(), order.getStatus().name());
    }

    /**
     * Milestone 7: the refund module's entry point into payment data. Never exposes
     * the Payment entity itself - ModuleBoundaryTest forbids refund from touching
     * payment.repository directly, so this facade method (and applyRefund below) are
     * the only way another module can read/mutate a payment's refund tracking.
     */
    @Transactional(readOnly = true)
    public PaymentSummaryView getSucceededPaymentSummary(UUID orderId) {
        Payment payment = paymentRepository
                .findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED)
                .orElseThrow(() -> new IllegalStateException("No succeeded payment for order: " + orderId));
        return toSummaryView(payment);
    }

    /**
     * Validates and applies a refund against the order's succeeded payment in one
     * step (Section 1.3: "Payment.totalRefundedAmount aynı transaction'da güncellenir").
     * Throws IllegalStateException if the refund would exceed what was actually paid.
     */
    @Transactional
    public PaymentSummaryView applyRefund(UUID orderId, long refundAmountMinorUnits) {
        Payment payment = paymentRepository
                .findByOrderIdAndStatusForUpdate(orderId, PaymentStatus.SUCCEEDED)
                .orElseThrow(() -> new IllegalStateException("No succeeded payment for order: " + orderId));
        payment.applyRefund(refundAmountMinorUnits);
        payment = paymentRepository.save(payment);
        return toSummaryView(payment);
    }

    /** Compensates a reserved refund amount when the provider reports a failed refund. */
    @Transactional
    public PaymentSummaryView releaseRefund(UUID orderId, long refundAmountMinorUnits) {
        Payment payment = paymentRepository
                .findByOrderIdAndStatusForUpdate(orderId, PaymentStatus.SUCCEEDED)
                .orElseThrow(() -> new IllegalStateException("No succeeded payment for order: " + orderId));
        payment.releaseRefund(refundAmountMinorUnits);
        payment = paymentRepository.save(payment);
        return toSummaryView(payment);
    }

    private PaymentSummaryView toSummaryView(Payment payment) {
        return new PaymentSummaryView(
                payment.getId(),
                payment.getBusinessId(),
                payment.getProviderPaymentIntentId(),
                payment.getAmountMinorUnits(),
                payment.getTotalRefundedAmountMinorUnits(),
                payment.getStatus().name());
    }

    private PaymentIntentView toView(Payment payment, CustomerOrder order) {
        return new PaymentIntentView(
                payment.getId(), order.getId(), payment.getStatus().name(), payment.getAmountMinorUnits(), payment.getProvider());
    }
}
