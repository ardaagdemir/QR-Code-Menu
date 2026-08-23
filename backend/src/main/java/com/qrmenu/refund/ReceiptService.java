package com.qrmenu.refund;

import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.OrderItem;
import com.qrmenu.ordering.OrderTrackingView;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.tenant.BranchDisplayInfo;
import com.qrmenu.tenant.TenantService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lives in the refund module (not ordering) on purpose: a receipt needs order + refund
 * data together, and ordering must never depend on refund (refund already depends on
 * ordering via payment - ordering -> refund would close a cycle). refund -> ordering,
 * refund -> tenant, refund -> (payment, transitively via nothing needed here) has no
 * such problem, so this is the only cycle-free home for it.
 */
@Service
public class ReceiptService {

    private final OrderingService orderingService;
    private final RefundService refundService;
    private final TenantService tenantService;

    public ReceiptService(OrderingService orderingService, RefundService refundService, TenantService tenantService) {
        this.orderingService = orderingService;
        this.refundService = refundService;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public ReceiptView getReceiptByTrackingToken(String rawToken) {
        OrderTrackingView tracking = orderingService.getOrderTrackingView(rawToken);
        return buildReceipt(tracking);
    }

    private ReceiptView buildReceipt(OrderTrackingView tracking) {
        CustomerOrder order = tracking.order();
        BranchDisplayInfo branchInfo = tenantService.getBranchDisplayInfo(order.getBranchId());
        List<RefundView> refunds = refundService.getRefundsForOrder(order.getId());
        // Only a COMPLETED refund actually moved money - a REQUESTED/PROCESSING one
        // hasn't yet, and a FAILED one never did (Payment.releaseRefund already
        // reverted its reservation). Summing every row regardless of status would
        // show the customer money as "refunded" that's still sitting with the
        // business, e.g. after a provider-side refund failure on a rejected order.
        long totalRefunded = refunds.stream()
                .filter(refund -> refund.status().equals(RefundStatus.COMPLETED.name()))
                .mapToLong(RefundView::totalAmountMinorUnits)
                .sum();
        List<ReceiptItemView> items =
                tracking.items().stream().map(ReceiptService::toItemView).toList();

        return new ReceiptView(
                branchInfo.businessName(),
                branchInfo.branchName(),
                order.getOrderNumber(),
                order.getCreatedAt(),
                items,
                order.getTotalMinorUnits(),
                totalRefunded,
                order.getTotalMinorUnits() - totalRefunded,
                refunds);
    }

    private static ReceiptItemView toItemView(OrderItem item) {
        return new ReceiptItemView(
                item.getProductNameSnapshot(), item.getOrderedQuantity(), item.getUnitPriceMinorUnits(), item.getLineTotalMinorUnits());
    }
}
