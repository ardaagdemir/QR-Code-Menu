package com.qrmenu.shared.outbox;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Single-instance @Scheduled poller (Section 2/12: no Kafka/message queue for v1) that
 * republishes unpublished outbox rows as in-process Spring application events. Mirrors
 * OrderCleanupScheduler's shape: package-private @Component, ISO-8601 fixedDelay via
 * @Scheduled. Each row is published via {@link OutboxEventItemPublisher} and isolated
 * with a try/catch here - a listener throwing for one event is logged and skipped
 * instead of rolling back markPublished() already committed for events processed
 * earlier in the same poll cycle, or blocking the rest of the batch from being
 * published.
 */
@Component
class OutboxPollerScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollerScheduler.class);

    private final OutboxEventRepository repository;
    private final OutboxEventItemPublisher outboxEventItemPublisher;

    OutboxPollerScheduler(OutboxEventRepository repository, OutboxEventItemPublisher outboxEventItemPublisher) {
        this.repository = repository;
        this.outboxEventItemPublisher = outboxEventItemPublisher;
    }

    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT5S")
    void publishPendingEvents() {
        List<OutboxEvent> pending = repository.findAllByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                outboxEventItemPublisher.publishEvent(event.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}; skipping to the next event", event.getId(), e);
            }
        }
    }
}
