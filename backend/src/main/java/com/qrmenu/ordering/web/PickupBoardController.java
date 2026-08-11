package com.qrmenu.ordering.web;

import com.qrmenu.notification.sse.SseOrderStatusNotifier;
import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.ordering.web.dto.PickupBoardEntryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Section 4, staff-web screen #8: "Pickup board - CUSTOMER_PICKUP şubeleri için kiosk
 * modunda sipariş numarası ekranı." Public/unauthenticated (Section 5's threat model is
 * about placing orders under someone else's table, not about a branchId-scoped read of
 * order numbers that are, by definition, already displayed on a public kiosk screen) -
 * same posture as PublicMenuController. Reuses the KDS's branch-kitchen SSE channel
 * (any order status change on the branch, not just READY ones) purely as a "something
 * changed, refetch" signal, same pattern as the KDS board.
 */
@RestController
@RequestMapping("/api/branches/{branchId}/pickup-board")
public class PickupBoardController {

    private final OrderingService orderingService;
    private final SseOrderStatusNotifier sseOrderStatusNotifier;

    public PickupBoardController(OrderingService orderingService, SseOrderStatusNotifier sseOrderStatusNotifier) {
        this.orderingService = orderingService;
        this.sseOrderStatusNotifier = sseOrderStatusNotifier;
    }

    @GetMapping
    public List<PickupBoardEntryResponse> getPickupBoard(@PathVariable UUID branchId) {
        return orderingService.getPickupBoard(branchId).stream().map(PickupBoardController::toResponse).toList();
    }

    @GetMapping("/stream")
    public SseEmitter stream(@PathVariable UUID branchId) {
        return sseOrderStatusNotifier.subscribeToBranchKitchen(branchId);
    }

    private static PickupBoardEntryResponse toResponse(CustomerOrder order) {
        return new PickupBoardEntryResponse(order.getId(), order.getOrderNumber());
    }
}
