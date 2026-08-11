package com.qrmenu.payment;

import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.payment.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the dev/mock-only "hosted payment screen" trigger (Section 2, mock flow step
 * 2): validates the caller owns the order and the payment is actually awaiting an
 * outcome, then hands off to the async dispatcher. A real provider integration has no
 * equivalent of this class - the customer would be redirected to the provider's own
 * hosted checkout page instead - so it only exists while payment.provider=mock.
 */
@Service
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentSimulationService {

    private final OrderingService orderingService;
    private final PaymentRepository paymentRepository;
    private final MockPaymentSimulationDispatcher dispatcher;

    public MockPaymentSimulationService(
            OrderingService orderingService, PaymentRepository paymentRepository, MockPaymentSimulationDispatcher dispatcher) {
        this.orderingService = orderingService;
        this.paymentRepository = paymentRepository;
        this.dispatcher = dispatcher;
    }

    @Transactional(readOnly = true)
    public void triggerOutcome(UUID tableVisitId, UUID callerSessionId, UUID paymentId, WebhookOutcome outcome) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        // Ownership check only - the result is intentionally discarded (Section 5: 404
        // on a cookie/table-visit mismatch, same as every other cart/order operation).
        orderingService.getOwnedOrder(tableVisitId, callerSessionId, payment.getOrderId());
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Payment is not awaiting an outcome: " + paymentId);
        }
        dispatcher.dispatch(payment.getProviderPaymentIntentId(), outcome);
    }
}
