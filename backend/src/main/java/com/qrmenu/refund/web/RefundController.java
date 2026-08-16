package com.qrmenu.refund.web;

import com.qrmenu.ordering.OrderItem;
import com.qrmenu.ordering.OrderTrackingView;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.refund.RefundService;
import com.qrmenu.refund.RefundService.RefundLineRequest;
import com.qrmenu.refund.RefundView;
import com.qrmenu.refund.web.dto.CreateRefundRequest;
import com.qrmenu.refund.web.dto.RefundItemResponse;
import com.qrmenu.refund.web.dto.RefundResponse;
import com.qrmenu.refund.web.dto.StaffOrderItemResponse;
import com.qrmenu.refund.web.dto.StaffOrderLookupResponse;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-facing refund API (Section 4, screen #6: "tam/kısmi iade başlatma"). Moved off
 * /api/kitchen/** (product decision: no separate Mutfak/KDS concept survives anywhere,
 * including in URLs) onto /api/staff/branches/{branchId}/orders, the same base path as
 * OrderControlController - the two controllers' route suffixes don't overlap (this one
 * owns /search, /{orderId}/refunds, /{orderId}/complete; OrderControlController owns
 * everything else under this prefix), so both can coexist under the shared prefix.
 * Permission.REFUND_ISSUE, scoped to this branchId, guards access.
 */
@RestController
@RequestMapping({"/api/staff/orders", "/api/staff/branches/{branchId}/orders"})
public class RefundController {

    private final OrderingService orderingService;
    private final RefundService refundService;
    private final StaffAuthService staffAuthService;

    public RefundController(OrderingService orderingService, RefundService refundService, StaffAuthService staffAuthService) {
        this.orderingService = orderingService;
        this.refundService = refundService;
        this.staffAuthService = staffAuthService;
    }

    /** Order lookup by its readable order number - what staff would actually have on hand to start a refund. */
    @GetMapping("/search")
    public StaffOrderLookupResponse search(
            @PathVariable(required = false) UUID branchId,
            @RequestParam int orderNumber,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        branchId = requireRefundAccess(sessionCookie, branchId).activeBranchId();
        OrderTrackingView tracking = orderingService.getOrderByNumber(branchId, orderNumber);
        return toLookupResponse(tracking);
    }

    @PostMapping("/{orderId}/refunds")
    public RefundResponse createRefund(
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID orderId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateRefundRequest request) {
        StaffContext context = requireRefundAccess(sessionCookie, branchId);
        branchId = context.activeBranchId();
        List<RefundLineRequest> lines =
                request.items().stream().map(item -> new RefundLineRequest(item.orderItemId(), item.quantity())).toList();
        return toResponse(refundService.requestRefund(branchId, orderId, lines, context.staffUserId()));
    }

    @GetMapping("/{orderId}/refunds")
    public List<RefundResponse> listRefunds(
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID orderId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        branchId = requireRefundAccess(sessionCookie, branchId).activeBranchId();
        orderingService.getOrderInBranch(branchId, orderId);
        return refundService.getRefundsForOrder(orderId).stream().map(RefundController::toResponse).toList();
    }

    /**
     * Section 4, screen #6: "teslim işlemi" - staff confirms a READY order was delivered
     * (WAITER_DELIVERY) or picked up (CUSTOMER_PICKUP). Permission.ORDER_COMPLETE,
     * branch-scoped like every other action here.
     */
    @PostMapping("/{orderId}/complete")
    public StaffOrderLookupResponse completeOrder(
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID orderId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        UUID sessionId = StaffCookieSupport.parseSessionId(sessionCookie);
        StaffContext context = branchId == null
                ? staffAuthService.resolveStaffContextForActiveBranch(sessionId, Permission.ORDER_COMPLETE)
                : staffAuthService.resolveStaffContextForBranch(sessionId, Permission.ORDER_COMPLETE, branchId);
        branchId = context.activeBranchId();
        orderingService.completeOrder(branchId, orderId);
        return toLookupResponse(orderingService.getOrderTrackingViewInBranch(branchId, orderId));
    }

    private StaffContext requireRefundAccess(String sessionCookie, UUID branchId) {
        UUID sessionId = StaffCookieSupport.parseSessionId(sessionCookie);
        return branchId == null
                ? staffAuthService.resolveStaffContextForActiveBranch(sessionId, Permission.REFUND_ISSUE)
                : staffAuthService.resolveStaffContextForBranch(sessionId, Permission.REFUND_ISSUE, branchId);
    }

    private StaffOrderLookupResponse toLookupResponse(OrderTrackingView tracking) {
        List<StaffOrderItemResponse> items =
                tracking.items().stream().map(RefundController::toItemResponse).toList();
        List<RefundResponse> refunds =
                refundService.getRefundsForOrder(tracking.order().getId()).stream().map(RefundController::toResponse).toList();
        return new StaffOrderLookupResponse(
                tracking.order().getId(),
                tracking.order().getOrderNumber(),
                tracking.order().getStatus().name(),
                tracking.order().getTotalMinorUnits(),
                items,
                refunds);
    }

    private static StaffOrderItemResponse toItemResponse(OrderItem item) {
        return new StaffOrderItemResponse(
                item.getId(),
                item.getProductNameSnapshot(),
                item.getOrderedQuantity(),
                item.getAcceptedQuantity(),
                item.getRejectedQuantity(),
                item.getStatus().name(),
                item.getUnitPriceMinorUnits(),
                item.getLineTotalMinorUnits());
    }

    private static RefundResponse toResponse(RefundView view) {
        List<RefundItemResponse> items = view.items().stream()
                .map(item -> new RefundItemResponse(item.orderItemId(), item.refundedQuantity(), item.refundAmountMinorUnits()))
                .toList();
        return new RefundResponse(view.refundId(), view.orderId(), view.status(), view.totalAmountMinorUnits(), view.createdAt(), items);
    }
}
