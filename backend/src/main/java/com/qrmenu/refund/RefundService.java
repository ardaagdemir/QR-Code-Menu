package com.qrmenu.refund;

import com.qrmenu.audit.AuditService;
import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.OrderItem;
import com.qrmenu.ordering.OrderTrackingView;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.payment.PaymentProviderPort;
import com.qrmenu.payment.PaymentService;
import com.qrmenu.payment.PaymentSummaryView;
import com.qrmenu.refund.repository.RefundItemRepository;
import com.qrmenu.refund.repository.RefundRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the refund module (Section 4, staff-web screen #6: "tam/kısmi iade
 * başlatma"). Every entry point here is still only ever called from a controller
 * (never from ordering/OrderingService itself) - requestFullRefund is triggered by
 * OrderControlController right after a cashier REJECT, not by ordering internally, for
 * the same module-cycle-avoidance reason as the original Milestone 7 decision
 * (ordering -> refund -> payment -> ordering).
 */
@Service
public class RefundService {

    private final OrderingService orderingService;
    private final PaymentService paymentService;
    private final PaymentProviderPort paymentProvider;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final AuditService auditService;

    public RefundService(
            OrderingService orderingService,
            PaymentService paymentService,
            PaymentProviderPort paymentProvider,
            RefundRepository refundRepository,
            RefundItemRepository refundItemRepository,
            AuditService auditService) {
        this.orderingService = orderingService;
        this.paymentService = paymentService;
        this.paymentProvider = paymentProvider;
        this.refundRepository = refundRepository;
        this.refundItemRepository = refundItemRepository;
        this.auditService = auditService;
    }

    public record RefundLineRequest(UUID orderItemId, int quantity) {
    }

    /**
     * Prices every line from the OrderItem's own immutable snapshot (Section 9:
     * "backend'de hesaplanır" - never trusts a caller-supplied amount), then applies
     * the whole refund against the order's succeeded payment in one step
     * (PaymentService.applyRefund enforces the "toplam iade tutarı ... aşamaz"
     * invariant - Section 1.3 - inside the same transaction as this method).
     */
    @Transactional
    public RefundView requestRefund(UUID branchId, UUID orderId, List<RefundLineRequest> lines, UUID actorStaffUserId) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Refund must include at least one item");
        }
        CustomerOrder order = orderingService.getOrderInBranch(branchId, orderId);

        record PricedLine(UUID orderItemId, int quantity, long amountMinorUnits) {
        }
        List<PricedLine> pricedLines = new ArrayList<>();
        long totalAmount = 0;
        for (RefundLineRequest line : lines) {
            OrderItem item = orderingService.getOrderItem(order.getId(), line.orderItemId());
            if (line.quantity() <= 0 || line.quantity() > item.getOrderedQuantity()) {
                throw new IllegalArgumentException("Invalid refund quantity for item: " + line.orderItemId());
            }
            long amount = item.getUnitPriceMinorUnits() * line.quantity();
            pricedLines.add(new PricedLine(line.orderItemId(), line.quantity(), amount));
            totalAmount += amount;
        }

        PaymentSummaryView paymentSummary = paymentService.applyRefund(order.getId(), totalAmount);

        Refund refund = refundRepository.save(new Refund(order.getBusinessId(), order.getId(), paymentSummary.paymentId(), totalAmount));
        List<RefundItem> items = new ArrayList<>();
        for (PricedLine line : pricedLines) {
            items.add(refundItemRepository.save(new RefundItem(refund.getId(), line.orderItemId(), line.quantity(), line.amountMinorUnits())));
        }

        refund.markProcessing();
        paymentProvider.refund(paymentSummary.providerPaymentIntentId(), totalAmount);
        refund.markCompleted();
        refund = refundRepository.save(refund);

        auditService.record(
                order.getBusinessId(), actorStaffUserId, "Refund", refund.getId(), "ISSUED",
                Map.of("orderId", order.getId().toString(), "totalAmountMinorUnits", totalAmount));

        return toView(refund, items);
    }

    /**
     * Gap-analysis #1 (kasa red -> tam refund, Section 6/7): refunds every item at its
     * full ordered quantity - the natural "full refund" for an order the store rejected
     * before the kitchen ever saw it, so nothing has been partially accepted/prepared
     * yet. Reuses requestRefund's existing pricing/invariant-check path rather than
     * duplicating it - a full refund is just the specific case where every line's
     * quantity equals what was ordered.
     */
    @Transactional
    public RefundView requestFullRefund(UUID branchId, UUID orderId, UUID actorStaffUserId) {
        OrderTrackingView tracking = orderingService.getOrderTrackingViewInBranch(branchId, orderId);
        List<RefundLineRequest> lines = tracking.items().stream()
                .map(item -> new RefundLineRequest(item.getId(), item.getOrderedQuantity()))
                .toList();
        return requestRefund(branchId, orderId, lines, actorStaffUserId);
    }

    @Transactional(readOnly = true)
    public List<RefundView> getRefundsForOrder(UUID orderId) {
        List<Refund> refunds = refundRepository.findAllByOrderIdOrderByCreatedAtAsc(orderId);
        List<UUID> refundIds = refunds.stream().map(Refund::getId).toList();
        Map<UUID, List<RefundItem>> itemsByRefundId = refundItemRepository.findAllByRefundIdIn(refundIds).stream()
                .collect(Collectors.groupingBy(RefundItem::getRefundId));
        return refunds.stream()
                .map(refund -> toView(refund, itemsByRefundId.getOrDefault(refund.getId(), List.of())))
                .toList();
    }

    private RefundView toView(Refund refund, List<RefundItem> items) {
        List<RefundItemView> itemViews = items.stream()
                .map(item -> new RefundItemView(item.getOrderItemId(), item.getRefundedQuantity(), item.getRefundAmountMinorUnits()))
                .toList();
        return new RefundView(
                refund.getId(), refund.getOrderId(), refund.getStatus().name(), refund.getTotalAmountMinorUnits(), refund.getCreatedAt(), itemViews);
    }
}
