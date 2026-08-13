package com.qrmenu.ordering.web;

import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.KitchenQueueOrderView;
import com.qrmenu.ordering.OrderItem;
import com.qrmenu.ordering.OrderItemOption;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.ordering.web.dto.OrderControlOrderItemOptionResponse;
import com.qrmenu.ordering.web.dto.OrderControlOrderItemResponse;
import com.qrmenu.ordering.web.dto.OrderControlOrderResponse;
import com.qrmenu.ordering.web.dto.RejectOrderRequest;
import com.qrmenu.refund.RefundService;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.tenant.TenantService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gap-analysis #1 / product-requirements.md Section 6: the cashier acceptance gate.
 * A verified payment webhook lands an order in AWAITING_STORE_ACCEPTANCE (see
 * OrderingService.markOrderAwaitingStoreAcceptance) - it never reaches the kitchen
 * queue on its own anymore. This controller is the only way out of that state:
 * ACCEPT (-> IN_KITCHEN) or REJECT (-> REJECTED_BY_STORE + full refund). REJECT
 * orchestrates ordering + refund from here (not from inside OrderingService) so
 * ordering never has to depend on the refund module - same module-cycle-avoidance
 * reasoning as the Milestone 7 decision this supersedes (see RefundService Javadoc).
 */
@RestController
@RequestMapping("/api/staff/branches/{branchId}/orders")
public class OrderControlController {

    private final OrderingService orderingService;
    private final RefundService refundService;
    private final StaffAuthService staffAuthService;
    private final TenantService tenantService;

    public OrderControlController(
            OrderingService orderingService, RefundService refundService, StaffAuthService staffAuthService, TenantService tenantService) {
        this.orderingService = orderingService;
        this.refundService = refundService;
        this.staffAuthService = staffAuthService;
        this.tenantService = tenantService;
    }

    /** Section 10.1: kasa dashboard'un "yeni ödenmiş/onay bekleyen siparişler" listesi. */
    @GetMapping("/pending-acceptance")
    public List<OrderControlOrderResponse> getPendingAcceptance(
            @PathVariable UUID branchId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        requireOrderAccess(sessionCookie, branchId, Permission.ORDER_VIEW);
        int timeoutSeconds = tenantService.getStoreAcceptanceTimeoutSeconds(branchId);
        return orderingService.getPendingStoreAcceptanceOrders(branchId).stream()
                .map(view -> toResponse(view, timeoutSeconds))
                .toList();
    }

    @PostMapping("/{orderId}/accept")
    public OrderControlOrderResponse accept(
            @PathVariable UUID branchId,
            @PathVariable UUID orderId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_ACCEPT);
        CustomerOrder order = orderingService.acceptOrder(branchId, orderId, context.staffUserId());
        return toResponse(order, tenantService.getStoreAcceptanceTimeoutSeconds(branchId));
    }

    @PostMapping("/{orderId}/reject")
    public OrderControlOrderResponse reject(
            @PathVariable UUID branchId,
            @PathVariable UUID orderId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody RejectOrderRequest request) {
        StaffContext context = requireOrderAccess(sessionCookie, branchId, Permission.ORDER_REJECT);
        orderingService.rejectOrder(branchId, orderId, request.reasonCode(), request.note(), context.staffUserId());
        refundService.requestFullRefund(branchId, orderId, context.staffUserId());
        CustomerOrder order = orderingService.getOrderInBranch(branchId, orderId);
        return toResponse(order, tenantService.getStoreAcceptanceTimeoutSeconds(branchId));
    }

    private StaffContext requireOrderAccess(String sessionCookie, UUID branchId, Permission permission) {
        return staffAuthService.resolveStaffContextForBranch(StaffCookieSupport.parseSessionId(sessionCookie), permission, branchId);
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
