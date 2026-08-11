package com.qrmenu.ordering.web;

import com.qrmenu.notification.sse.SseOrderStatusNotifier;
import com.qrmenu.ordering.OrderItem;
import com.qrmenu.ordering.OrderTrackingView;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.ordering.web.dto.OrderTrackingItemResponse;
import com.qrmenu.ordering.web.dto.OrderTrackingResponse;
import com.qrmenu.tenant.TenantService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Public, read-only order tracking (Section 5: "cihaz/cookie bağımsız takip" - the
 * orderTrackingToken itself is the access credential, not the qrmenu_session cookie).
 * No ownership/session check beyond the token matching a hashed row - by design, this
 * is the deliberately narrow backup access path Section 5 describes, never able to
 * mutate the order.
 */
@RestController
@RequestMapping("/api/order-tracking/{token}")
public class OrderTrackingController {

    private final OrderingService orderingService;
    private final SseOrderStatusNotifier sseOrderStatusNotifier;
    private final TenantService tenantService;

    public OrderTrackingController(
            OrderingService orderingService, SseOrderStatusNotifier sseOrderStatusNotifier, TenantService tenantService) {
        this.orderingService = orderingService;
        this.sseOrderStatusNotifier = sseOrderStatusNotifier;
        this.tenantService = tenantService;
    }

    @GetMapping
    public OrderTrackingResponse getTrackingStatus(@PathVariable String token) {
        return toResponse(orderingService.getOrderTrackingView(token));
    }

    @GetMapping("/stream")
    public SseEmitter stream(@PathVariable String token) {
        OrderTrackingView view = orderingService.getOrderTrackingView(token);
        return sseOrderStatusNotifier.subscribeToOrder(view.order().getId());
    }

    private OrderTrackingResponse toResponse(OrderTrackingView view) {
        List<OrderTrackingItemResponse> items = view.items().stream().map(OrderTrackingController::toItemResponse).toList();
        String deliveryModel = tenantService.getDeliveryModel(view.order().getBranchId()).name();
        return new OrderTrackingResponse(
                view.order().getId(),
                view.order().getOrderNumber(),
                view.order().getStatus().name(),
                view.order().getTotalMinorUnits(),
                deliveryModel,
                items);
    }

    private static OrderTrackingItemResponse toItemResponse(OrderItem item) {
        return new OrderTrackingItemResponse(
                item.getProductNameSnapshot(), item.getOrderedQuantity(), item.getAcceptedQuantity(), item.getRejectedQuantity(), item.getStatus().name());
    }
}
