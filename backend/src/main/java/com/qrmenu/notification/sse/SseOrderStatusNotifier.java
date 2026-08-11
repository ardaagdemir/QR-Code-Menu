package com.qrmenu.notification.sse;

import com.qrmenu.notification.OrderStatusNotifier;
import com.qrmenu.notification.OrderStatusUpdate;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Section 2: "Canlı durum güncellemeleri tek Spring Boot instance içi pub/sub ile
 * planlanıyor" - a plain in-memory registry, no external broker (Section 12: no
 * Kafka/message queue for v1; horizontal scaling with a shared channel like Redis
 * pub/sub is explicitly out of scope - Section 7). Two independent subscriber
 * dimensions share one notifier: a customer tracking a single order (subscribeToOrder)
 * and a KDS screen watching every order for a branch (subscribeToBranchKitchen) - one
 * status change notifies whichever of the two audiences is listening.
 */
@Component
public class SseOrderStatusNotifier implements OrderStatusNotifier {

    private static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);

    private final Map<UUID, List<SseEmitter>> orderSubscribers = new ConcurrentHashMap<>();
    private final Map<UUID, List<SseEmitter>> branchKitchenSubscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribeToOrder(UUID orderId) {
        return register(orderSubscribers, orderId);
    }

    public SseEmitter subscribeToBranchKitchen(UUID branchId) {
        return register(branchKitchenSubscribers, branchId);
    }

    @Override
    public void notifyOrderStatusChanged(OrderStatusUpdate update) {
        sendToAll(orderSubscribers.get(update.orderId()), update);
        sendToAll(branchKitchenSubscribers.get(update.branchId()), update);
    }

    /**
     * Without an initial write, Tomcat's async response never actually commits/flushes
     * the SSE headers to the socket until the first real event - a subscriber that
     * connects but sees no order activity for a while stays stuck looking like it never
     * connected at all (confirmed live: curl and EventSource both saw zero bytes,
     * indefinitely, against a freshly-subscribed emitter with no subsequent event).
     * Sending a no-op comment immediately forces that flush, so the client's "open"
     * fires right away regardless of when the first real event happens.
     */
    private SseEmitter register(Map<UUID, List<SseEmitter>> registry, UUID key) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        List<SseEmitter> emitters = registry.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * Two kitchen actions fired in quick succession (e.g. decide immediately followed
     * by ready, both against the same order/branch) each run on their own request
     * thread and both end up calling notifyOrderStatusChanged - meaning two threads can
     * call send() on the very same SseEmitter at nearly the same time. SseEmitter.send
     * is not internally synchronized for concurrent callers, so without the lock below,
     * two interleaved writes could corrupt the SSE stream's framing (confirmed live:
     * without this synchronized block, a rapid decide-then-ready pair reliably left the
     * customer tracking page's SSE connection stuck on the first status, apparently
     * dropping the second event).
     */
    private void sendToAll(List<SseEmitter> emitters, OrderStatusUpdate update) {
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name("order-status").data(update, MediaType.APPLICATION_JSON));
                }
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
                emitters.remove(emitter);
            }
        }
    }
}
