package com.qrmenu.refund.web;

import com.qrmenu.refund.ReceiptItemView;
import com.qrmenu.refund.ReceiptService;
import com.qrmenu.refund.ReceiptView;
import com.qrmenu.refund.RefundView;
import com.qrmenu.refund.web.dto.ReceiptItemResponse;
import com.qrmenu.refund.web.dto.ReceiptResponse;
import com.qrmenu.refund.web.dto.RefundItemResponse;
import com.qrmenu.refund.web.dto.RefundResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only printable receipt (Section 4, customer-web screen #10:
 * "yazdırılabilir HTML, yasal fatura değildir"). Same access model as
 * OrderTrackingController - the orderTrackingToken itself is the credential, no
 * cookie/session required.
 */
@RestController
@RequestMapping("/api/order-tracking/{token}")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/receipt")
    public ReceiptResponse getReceipt(@PathVariable String token) {
        return toResponse(receiptService.getReceiptByTrackingToken(token));
    }

    private static ReceiptResponse toResponse(ReceiptView view) {
        List<ReceiptItemResponse> items = view.items().stream().map(ReceiptController::toItemResponse).toList();
        List<RefundResponse> refunds = view.refunds().stream().map(ReceiptController::toRefundResponse).toList();
        return new ReceiptResponse(
                view.businessName(),
                view.branchName(),
                view.orderNumber(),
                view.orderCreatedAt(),
                items,
                view.totalMinorUnits(),
                view.totalRefundedMinorUnits(),
                view.netPaidMinorUnits(),
                refunds);
    }

    private static ReceiptItemResponse toItemResponse(ReceiptItemView item) {
        return new ReceiptItemResponse(item.productName(), item.quantity(), item.unitPriceMinorUnits(), item.lineTotalMinorUnits());
    }

    private static RefundResponse toRefundResponse(RefundView view) {
        List<RefundItemResponse> items = view.items().stream()
                .map(item -> new RefundItemResponse(item.orderItemId(), item.refundedQuantity(), item.refundAmountMinorUnits()))
                .toList();
        return new RefundResponse(view.refundId(), view.orderId(), view.status(), view.totalAmountMinorUnits(), view.createdAt(), items);
    }
}
