package com.qrmenu.refund;

import com.qrmenu.audit.AuditService;
import com.qrmenu.notification.OrderStatusNotifier;
import com.qrmenu.notification.OrderStatusUpdate;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final OrderStatusNotifier orderStatusNotifier;

    public RefundService(
            OrderingService orderingService,
            PaymentService paymentService,
            PaymentProviderPort paymentProvider,
            RefundRepository refundRepository,
            RefundItemRepository refundItemRepository,
            AuditService auditService,
            OrderStatusNotifier orderStatusNotifier) {
        this.orderingService = orderingService;
        this.paymentService = paymentService;
        this.paymentProvider = paymentProvider;
        this.refundRepository = refundRepository;
        this.refundItemRepository = refundItemRepository;
        this.auditService = auditService;
        this.orderStatusNotifier = orderStatusNotifier;
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
        CustomerOrder order = orderingService.getOrderInBranchForUpdate(branchId, orderId);
        return executeRefund(order, lines, actorStaffUserId);
    }

    private RefundView executeRefund(CustomerOrder order, List<RefundLineRequest> lines, UUID actorStaffUserId) {
        Map<UUID, Integer> refundedQuantities = getCompletedRefundedQuantities(order.getId());
        Set<UUID> requestedItemIds = new HashSet<>();

        record PricedLine(UUID orderItemId, int quantity, long amountMinorUnits) {
        }
        List<PricedLine> pricedLines = new ArrayList<>();
        long totalAmount = 0;
        for (RefundLineRequest line : lines) {
            if (!requestedItemIds.add(line.orderItemId())) {
                throw new IllegalArgumentException("Duplicate refund item: " + line.orderItemId());
            }
            OrderItem item = orderingService.getOrderItem(order.getId(), line.orderItemId());
            int alreadyRefunded = refundedQuantities.getOrDefault(item.getId(), 0);
            int remainingRefundable = Math.max(0, item.getOrderedQuantity() - alreadyRefunded);
            if (line.quantity() <= 0 || line.quantity() > remainingRefundable) {
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
        try {
            paymentProvider.refund(paymentSummary.providerPaymentIntentId(), totalAmount);
            refund.markCompleted();
        } catch (RuntimeException providerFailure) {
            paymentService.releaseRefund(order.getId(), totalAmount);
            refund.markFailed();
        }
        refund = refundRepository.save(refund);

        auditService.record(
                order.getBusinessId(), actorStaffUserId, "Refund", refund.getId(),
                refund.getStatus() == RefundStatus.COMPLETED ? "ISSUED" : "FAILED",
                Map.of("orderId", order.getId().toString(), "totalAmountMinorUnits", totalAmount));

        // The order's own REJECTED_BY_STORE push (OrderingService.rejectOrder) already
        // reached the customer's SSE stream before this refund was even requested - the
        // provider call above is synchronous, but nothing guarantees the customer's
        // subsequent refetch lands after it resolves rather than mid-flight. Re-firing
        // the same "something changed, refetch" signal now that the refund has a final
        // COMPLETED/FAILED status closes that race instead of leaving the customer stuck
        // on a stale "iade başlatılacak" read until their next reload/reconnect.
        orderStatusNotifier.notifyOrderStatusChanged(
                new OrderStatusUpdate(order.getId(), order.getBranchId(), order.getStatus().name(), order.getOrderNumber()));

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
        CustomerOrder order = orderingService.getOrderInBranchForUpdate(branchId, orderId);
        OrderTrackingView tracking = orderingService.getOrderTrackingViewInBranch(branchId, orderId);
        Map<UUID, Integer> refundedQuantities = getCompletedRefundedQuantities(orderId);
        List<RefundLineRequest> lines = tracking.items().stream()
                .map(item -> new RefundLineRequest(
                        item.getId(), Math.max(0, item.getOrderedQuantity() - refundedQuantities.getOrDefault(item.getId(), 0))))
                .filter(line -> line.quantity() > 0)
                .toList();
        if (lines.isEmpty()) {
            throw new IllegalStateException("Order is already fully refunded: " + orderId);
        }
        return executeRefund(order, lines, actorStaffUserId);
    }

    /** Gap-analysis #8 reporting: total completed refund amount for a set of orders (Section 13.4, refund toplamı). */
    @Transactional(readOnly = true)
    public long sumCompletedRefundAmount(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return 0L;
        }
        return refundRepository.sumTotalAmountMinorUnitsByOrderIdInAndStatus(orderIds, RefundStatus.COMPLETED);
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

    /** Completed quantity totals back both API presentation and refund validation. */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> getCompletedRefundedQuantities(UUID orderId) {
        Map<UUID, Integer> quantities = new HashMap<>();
        for (Object[] row : refundItemRepository.sumCompletedRefundedQuantityByOrderItem(orderId)) {
            quantities.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return Map.copyOf(quantities);
    }

    private RefundView toView(Refund refund, List<RefundItem> items) {
        List<RefundItemView> itemViews = items.stream()
                .map(item -> new RefundItemView(item.getOrderItemId(), item.getRefundedQuantity(), item.getRefundAmountMinorUnits()))
                .toList();
        return new RefundView(
                refund.getId(), refund.getOrderId(), refund.getStatus().name(), refund.getTotalAmountMinorUnits(), refund.getCreatedAt(), itemViews);
    }
}
