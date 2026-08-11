package com.qrmenu.ordering.web;

import com.qrmenu.customersession.SessionCookieSupport;
import com.qrmenu.ordering.CartView;
import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.OrderItem;
import com.qrmenu.ordering.OrderItemOption;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.ordering.web.dto.AddCartItemRequest;
import com.qrmenu.ordering.web.dto.CartItemOptionResponse;
import com.qrmenu.ordering.web.dto.CartItemResponse;
import com.qrmenu.ordering.web.dto.CartResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, anonymous, customer-facing DRAFT-order/cart API (Section 3: "Sepet ayrı bir
 * modül değil" - the DRAFT Order itself is the cart). Every operation is scoped to a
 * TableVisit and authenticated by the caller's qrmenu_session cookie
 * (OrderingService.getOwnedTableVisit via CustomerSessionService), not by the
 * tableVisitId alone.
 */
@RestController
@RequestMapping("/api/table-visits/{tableVisitId}/cart")
public class CartController {

    private final OrderingService orderingService;

    public CartController(OrderingService orderingService) {
        this.orderingService = orderingService;
    }

    @GetMapping
    public CartResponse getCart(
            @PathVariable UUID tableVisitId,
            @CookieValue(name = SessionCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        UUID sessionId = SessionCookieSupport.parseSessionId(sessionCookie);
        return orderingService
                .getCart(tableVisitId, sessionId)
                .map(CartController::toResponse)
                .orElseGet(() -> CartResponse.empty(tableVisitId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @PathVariable UUID tableVisitId,
            @CookieValue(name = SessionCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody AddCartItemRequest request) {
        UUID sessionId = SessionCookieSupport.parseSessionId(sessionCookie);
        CartView view = orderingService.addItem(tableVisitId, sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(view));
    }

    @DeleteMapping("/items/{orderItemId}")
    public CartResponse removeItem(
            @PathVariable UUID tableVisitId,
            @PathVariable UUID orderItemId,
            @CookieValue(name = SessionCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        UUID sessionId = SessionCookieSupport.parseSessionId(sessionCookie);
        CartView view = orderingService.removeItem(tableVisitId, sessionId, orderItemId);
        return toResponse(view);
    }

    private static CartResponse toResponse(CartView view) {
        CustomerOrder order = view.order();
        List<CartItemResponse> items = view.items().stream()
                .map(item -> toItemResponse(item, view.optionsByItemId().getOrDefault(item.getId(), List.of())))
                .toList();
        return new CartResponse(
                order.getTableVisitId(),
                order.getId(),
                order.getStatus().name(),
                order.getTotalMinorUnits(),
                items,
                view.newlyIssuedTrackingToken());
    }

    private static CartItemResponse toItemResponse(OrderItem item, List<OrderItemOption> options) {
        List<CartItemOptionResponse> optionResponses = options.stream()
                .map(option -> new CartItemOptionResponse(
                        option.getId(), option.getOptionNameSnapshot(), option.getPriceDeltaMinorUnits()))
                .toList();
        return new CartItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductNameSnapshot(),
                item.getUnitPriceMinorUnits(),
                item.getOrderedQuantity(),
                item.getLineTotalMinorUnits(),
                optionResponses);
    }
}
