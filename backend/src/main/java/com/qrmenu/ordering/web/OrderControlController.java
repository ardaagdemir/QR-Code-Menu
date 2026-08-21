package com.qrmenu.ordering.web;

import com.qrmenu.notification.sse.SseOrderStatusNotifier;
import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.KitchenQueueOrderView;
import com.qrmenu.ordering.OrderItem;
import com.qrmenu.ordering.OrderItemOption;
import com.qrmenu.ordering.OrderStatus;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.ordering.web.dto.OrderControlOrderItemOptionResponse;
import com.qrmenu.ordering.web.dto.OrderControlOrderItemResponse;
import com.qrmenu.ordering.web.dto.OrderControlOrderResponse;
import com.qrmenu.ordering.web.dto.OrderHistoryResponse;
import com.qrmenu.ordering.web.dto.RejectOrderRequest;
import com.qrmenu.refund.RefundService;
import com.qrmenu.refund.RefundView;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.tenant.TenantService;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Gap-analysis #1 / product-requirements.md Section 6: the cashier acceptance gate,
 * and (product decision: no separate Mutfak/KDS screen) the single operational
 * surface for every order-lifecycle action a business role performs. A verified
 * payment webhook lands an order in AWAITING_STORE_ACCEPTANCE (see
 * OrderingService.markOrderAwaitingStoreAcceptance) - it never reaches the
 * in-progress queue on its own. This controller is the only way out of that state:
 * ACCEPT (-> IN_KITCHEN) or REJECT (-> REJECTED_BY_STORE + full refund). REJECT
 * orchestrates ordering + refund from here (not from inside OrderingService) so
 * ordering never has to depend on the refund module - same module-cycle-avoidance
 * reasoning as the Milestone 7 decision this supersedes (see RefundService Javadoc).
 * The in-progress/stream endpoints below used to live on a separate KitchenController
 * (/api/kitchen/**, Permission.KITCHEN_DECIDE) - that screen and role no longer exist,
 * so they were folded in here under Permission.ORDER_VIEW (read) / Permission.
 * ORDER_PREPARE (advance), the same business roles (BUSINESS_ADMIN/BRANCH_MANAGER/
 * CASHIER) that accept/reject. Product decision: there is no item-level kitchen
 * decision step anymore either - ACCEPT auto-accepts every item (OrderingService.
 * acceptOrder), and READY is a single order-level action below (/ready), not a
 * per-item ready/served rollup. The flow is exactly AWAITING_STORE_ACCEPTANCE -> ACCEPT
 * -> PREPARING -> READY -> COMPLETED (COMPLETED via RefundController's /complete,
 * unchanged).
 */
@RestController
@RequestMapping({"/api/staff/orders", "/api/staff/branches/{branchId}/orders"})
public class OrderControlController {

    private final OrderingService orderingService;
    private final RefundService refundService;
    private final StaffAuthService staffAuthService;
    private final TenantService tenantService;
    private final SseOrderStatusNotifier sseOrderStatusNotifier;

    public OrderControlController(
            OrderingService orderingService,
            RefundService refundService,
            StaffAuthService staffAuthService,
            TenantService tenantService,
            SseOrderStatusNotifier sseOrderStatusNotifier) {
        this.orderingService = orderingService;
        this.refundService = refundService;
        this.staffAuthService = staffAuthService;
        this.tenantService = tenantService;
        this.sseOrderStatusNotifier = sseOrderStatusNotifier;
    }

    /** Section 10.1: kasa dashboard'un "yeni ödenmiş/onay bekleyen siparişler" listesi. */
    @GetMapping("/pending-acceptance")
    public List<OrderControlOrderResponse> getPendingAcceptance(
            @PathVariable(required = false) UUID branchId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        branchId = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_VIEW).activeBranchId();
        int timeoutSeconds = tenantService.getStoreAcceptanceTimeoutSeconds(branchId);
        return orderingService.getPendingStoreAcceptanceOrders(branchId).stream()
                .map(view -> toResponse(view, timeoutSeconds))
                .toList();
    }

    @PostMapping("/{orderId}/accept")
    public OrderControlOrderResponse accept(
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID orderId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_ACCEPT);
        branchId = context.activeBranchId();
        CustomerOrder order = orderingService.acceptOrder(branchId, orderId, context.staffUserId());
        return toResponse(order, tenantService.getStoreAcceptanceTimeoutSeconds(branchId));
    }

    @PostMapping("/{orderId}/reject")
    public OrderControlOrderResponse reject(
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID orderId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody RejectOrderRequest request) {
        StaffContext context = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_REJECT);
        branchId = context.activeBranchId();
        orderingService.rejectOrder(branchId, orderId, request.reasonCode(), request.note(), context.staffUserId());
        refundService.requestFullRefund(branchId, orderId, context.staffUserId());
        CustomerOrder order = orderingService.getOrderInBranch(branchId, orderId);
        return toResponse(order, tenantService.getStoreAcceptanceTimeoutSeconds(branchId));
    }

    /** Every ACCEPTed order still being prepared (Section 6/8: PREPARING -> READY). */
    @GetMapping("/in-progress")
    public List<OrderControlOrderResponse> getInProgress(
            @PathVariable(required = false) UUID branchId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        branchId = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_VIEW).activeBranchId();
        int timeoutSeconds = tenantService.getStoreAcceptanceTimeoutSeconds(branchId);
        return orderingService.getKitchenQueue(branchId).stream().map(view -> toResponse(view, timeoutSeconds)).toList();
    }

    /** Every READY order awaiting hand-off/pickup (Section 6/8: "Hazır - Teslim Bekliyor"). */
    @GetMapping("/ready")
    public List<OrderControlOrderResponse> getReady(
            @PathVariable(required = false) UUID branchId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        branchId = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_VIEW).activeBranchId();
        int timeoutSeconds = tenantService.getStoreAcceptanceTimeoutSeconds(branchId);
        return orderingService.getReadyOrders(branchId).stream().map(view -> toResponse(view, timeoutSeconds)).toList();
    }

    /**
     * Siparişler (order history) screen: completed/rejected orders stay reachable after
     * they drop off the Kasa board (which only ever shows AWAITING_STORE_ACCEPTANCE/
     * IN_KITCHEN/READY - see the class javadoc). Defaults to COMPLETED+REJECTED_BY_STORE
     * when no status filter is given; from/to are branch-local calendar dates, same
     * convention as StaffReportingController.
     */
    @GetMapping("/history")
    public List<OrderHistoryResponse> getHistory(
            @PathVariable(required = false) UUID branchId,
            @RequestParam(required = false) List<String> status,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        branchId = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_VIEW).activeBranchId();
        List<OrderStatus> statuses = (status == null || status.isEmpty())
                ? List.of(OrderStatus.COMPLETED, OrderStatus.REJECTED_BY_STORE)
                : status.stream().map(OrderStatus::valueOf).toList();
        return orderingService.getOrderHistory(branchId, statuses, from, to).stream().map(this::toHistoryResponse).toList();
    }

    /**
     * Initial connect delivers no backlog - the caller already loaded pending/in-progress
     * orders via the GET endpoints above; this is purely a "something changed, refetch"
     * signal (staff-web's Kasa page uses it for both lists).
     */
    @GetMapping("/stream")
    public SseEmitter stream(
            @PathVariable(required = false) UUID branchId, @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        branchId = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_VIEW).activeBranchId();
        return sseOrderStatusNotifier.subscribeToBranchKitchen(branchId);
    }

    /** PREPARING -> READY, the whole order at once (Section 6/8) - no item-level decision step. */
    @PostMapping("/{orderId}/ready")
    public OrderControlOrderResponse markReady(
            @PathVariable(required = false) UUID branchId,
            @PathVariable UUID orderId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        branchId = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_PREPARE).activeBranchId();
        return toResponse(orderingService.markOrderReady(branchId, orderId), tenantService.getStoreAcceptanceTimeoutSeconds(branchId));
    }

    private StaffContext requireOrderAccess(String sessionCookie, UUID branchId, Permission permission) {
        UUID sessionId = StaffCookieSupport.parseSessionId(sessionCookie);
        return branchId == null
                ? staffAuthService.resolveStaffContextForActiveBranch(sessionId, permission)
                : staffAuthService.resolveStaffContextForBranch(sessionId, permission, branchId);
    }

    /** Accept/reject responses aren't rendered as cards (the frontend just refetches the list afterward), so tableLabel is skipped here. */
    private static OrderControlOrderResponse toResponse(CustomerOrder order, int timeoutSeconds) {
        return new OrderControlOrderResponse(
                order.getId(), order.getOrderNumber(), order.getStatus().name(), order.getTotalMinorUnits(),
                order.getRejectionReasonCode(), order.getRejectionNote(), null, order.getLastActivityAt(), timeoutSeconds, List.of());
    }

    private static OrderControlOrderResponse toResponse(KitchenQueueOrderView view, int timeoutSeconds) {
        List<OrderControlOrderItemResponse> items = view.items().stream()
                .map(item -> toItemResponse(item, view.optionsByItemId().getOrDefault(item.getId(), List.of())))
                .toList();
        return new OrderControlOrderResponse(
                view.order().getId(),
                view.order().getOrderNumber(),
                view.order().getStatus().name(),
                view.order().getTotalMinorUnits(),
                view.order().getRejectionReasonCode(),
                view.order().getRejectionNote(),
                view.tableLabel(),
                view.order().getLastActivityAt(),
                timeoutSeconds,
                items);
    }

    /** Most recently created refund's status, if the order has any - same "latest" convention as OrderTrackingController. */
    private OrderHistoryResponse toHistoryResponse(KitchenQueueOrderView view) {
        List<RefundView> refunds = refundService.getRefundsForOrder(view.order().getId());
        String latestRefundStatus = refunds.isEmpty() ? null : refunds.get(refunds.size() - 1).status();
        List<OrderControlOrderItemResponse> items = view.items().stream()
                .map(item -> toItemResponse(item, view.optionsByItemId().getOrDefault(item.getId(), List.of())))
                .toList();
        return new OrderHistoryResponse(
                view.order().getId(),
                view.order().getOrderNumber(),
                view.order().getStatus().name(),
                view.order().getTotalMinorUnits(),
                view.order().getRejectionReasonCode(),
                view.order().getRejectionNote(),
                view.tableLabel(),
                view.order().getCreatedAt(),
                view.order().getCompletedAt(),
                latestRefundStatus,
                items);
    }

    private static OrderControlOrderItemResponse toItemResponse(OrderItem item, List<OrderItemOption> options) {
        List<OrderControlOrderItemOptionResponse> optionResponses = options.stream()
                .map(option -> new OrderControlOrderItemOptionResponse(option.getId(), option.getOptionNameSnapshot()))
                .toList();
        return new OrderControlOrderItemResponse(
                item.getId(),
                item.getProductNameSnapshot(),
                item.getOrderedQuantity(),
                item.getAcceptedQuantity(),
                item.getRejectedQuantity(),
                item.getStatus().name(),
                optionResponses);
    }
}
