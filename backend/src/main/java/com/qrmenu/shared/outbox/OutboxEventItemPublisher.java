package com.qrmenu.shared.outbox;

import com.qrmenu.common.web.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes exactly one outbox row per call, in its own REQUIRES_NEW transaction (same
 * reasoning as PaymentWebhookIdempotencyGuard) - a separate bean, not a method called via
 * `this.` on OutboxPollerScheduler, since self-invocation bypasses the Spring proxy
 * entirely and @Transactional would silently not apply. REQUIRES_NEW (not just a plain
 * @Transactional) means a listener throwing for this event rolls back only this event's
 * own transaction, immediately and for real - not merely marks an ambient transaction
 * rollback-only - so it can't undo markPublished() already committed for events
 * processed earlier in the same poll cycle, and doesn't stop the remaining pending
 * events from being published.
 */
@Component
class OutboxEventItemPublisher {

    private final OutboxEventRepository repository;
    private final ApplicationEventPublisher applicationEventPublisher;

    OutboxEventItemPublisher(OutboxEventRepository repository, ApplicationEventPublisher applicationEventPublisher) {
        this.repository = repository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void publishEvent(UUID outboxEventId) {
        OutboxEvent event = repository
                .findById(outboxEventId)
                .orElseThrow(() -> new ResourceNotFoundException("OutboxEvent not found: " + outboxEventId));
        if (event.getPublishedAt() != null) {
            return;
        }
        applicationEventPublisher.publishEvent(new OutboxEventPublished(
                event.getId(), event.getAggregateType(), event.getAggregateId(), event.getEventType(), event.getPayload()));
        event.markPublished();
        repository.save(event);
    }
}
