package com.qrmenu.kitchen.web;

import com.qrmenu.kitchen.web.dto.DecideOrderItemRequest;
import com.qrmenu.kitchen.web.dto.KitchenOrderItemOptionResponse;
import com.qrmenu.kitchen.web.dto.KitchenOrderItemResponse;
import com.qrmenu.kitchen.web.dto.KitchenOrderResponse;
import com.qrmenu.notification.sse.SseOrderStatusNotifier;
import com.qrmenu.ordering.KitchenQueueOrderView;
import com.qrmenu.ordering.OrderItem;
import com.qrmenu.ordering.OrderItemOption;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * KDS query/command surface (Section 3: "KDS sorgu/komut modeli"). Every entity
 * mutation is delegated to OrderingService (ordering owns the Order/OrderItem tables -
 * ModuleBoundaryTest forbids any other module from touching ordering.repository
 * directly). Milestone 8: the temporary shared-secret filter (StaffAccessAuthFilter)
 * is gone - every handler now resolves+authorizes the real StaffUser session itself
 * (Permission.KITCHEN_DECIDE, scoped to this branchId), the same explicit
 * "read cookie, call the service" pattern CartController uses for customers.
 */
@RestController
@RequestMapping("/api/kitchen/branches/{branchId}")
public class KitchenController {

    private final OrderingService orderingService;
    private final SseOrderStatusNotifier sseOrderStatusNotifier;
    private final StaffAuthService staffAuthService;

    public KitchenController(OrderingService orderingService, SseOrderStatusNotifier sseOrderStatusNotifier, StaffAuthService staffAuthService) {
        this.orderingService = orderingService;
        this.sseOrderStatusNotifier = sseOrderStatusNotifier;
        this.staffAuthService = staffAuthService;
    }

    @GetMapping("/orders")
    public List<KitchenOrderResponse> getKitchenQueue(
            @PathVariable UUID branchId, @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        requireKitchenAccess(sessionCookie, branchId);
        return orderingService.getKitchenQueue(branchId).stream().map(KitchenController::toResponse).toList();
    }

    /**
     * Initial connect delivers no backlog - the caller already loaded the current
     * queue via getKitchenQueue. Auth is via the session cookie itself
     * (EventSource sends cookies cross-origin when opened with withCredentials:true -
     * see staff-web's buildKitchenStreamUrl - so, unlike Milestone 6, no query-param
     * token workaround is needed anymore).
     */
    @GetMapping("/stream")
    public SseEmitter stream(
            @PathVariable UUID branchId, @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        requireKitchenAccess(sessionCookie, branchId);
        return sseOrderStatusNotifier.subscribeToBranchKitchen(branchId);
    }

    @PostMapping("/order-items/{orderItemId}/decide")
    public KitchenOrderResponse decide(
            @PathVariable UUID branchId,
            @PathVariable UUID orderItemId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody DecideOrderItemRequest request) {
        requireKitchenAccess(sessionCookie, branchId);
        return toResponse(orderingService.decideOrderItem(branchId, orderItemId, request.acceptedQuantity()));
    }

    @PostMapping("/order-items/{orderItemId}/ready")
    public KitchenOrderResponse markReady(
            @PathVariable UUID branchId,
            @PathVariable UUID orderItemId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        requireKitchenAccess(sessionCookie, branchId);
        return toResponse(orderingService.markOrderItemReady(branchId, orderItemId));
    }

    @PostMapping("/order-items/{orderItemId}/served")
    public KitchenOrderResponse markServed(
            @PathVariable UUID branchId,
            @PathVariable UUID orderItemId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        requireKitchenAccess(sessionCookie, branchId);
        return toResponse(orderingService.markOrderItemServed(branchId, orderItemId));
    }

    private void requireKitchenAccess(String sessionCookie, UUID branchId) {
        staffAuthService.resolveStaffContextForBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.KITCHEN_DECIDE, branchId);
    }

    private static KitchenOrderResponse toResponse(KitchenQueueOrderView view) {
        List<KitchenOrderItemResponse> items = view.items().stream()
                .map(item -> toItemResponse(item, view.optionsByItemId().getOrDefault(item.getId(), List.of())))
                .toList();
        return new KitchenOrderResponse(
                view.order().getId(),
                view.order().getOrderNumber(),
                view.order().getStatus().name(),
                view.order().getTotalMinorUnits(),
                view.tableLabel(),
                view.order().getLastActivityAt(),
                items);
    }

    private static KitchenOrderItemResponse toItemResponse(OrderItem item, List<OrderItemOption> options) {
        List<KitchenOrderItemOptionResponse> optionResponses =
                options.stream().map(option -> new KitchenOrderItemOptionResponse(option.getId(), option.getOptionNameSnapshot())).toList();
        return new KitchenOrderItemResponse(
                item.getId(),
                item.getProductNameSnapshot(),
                item.getOrderedQuantity(),
                item.getAcceptedQuantity(),
                item.getRejectedQuantity(),
                item.getStatus().name(),
                optionResponses);
    }
}
