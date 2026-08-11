package com.qrmenu.shared.outbox;

import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-instance @Scheduled poller (Section 2/12: no Kafka/message queue for v1) that
 * republishes unpublished outbox rows as in-process Spring application events. Mirrors
 * OrderCleanupScheduler's shape: package-private @Component, ISO-8601 fixedDelay via
 * @Scheduled, @Transactional method.
 */
@Component
class OutboxPollerScheduler {

    private final OutboxEventRepository repository;
    private final ApplicationEventPublisher applicationEventPublisher;

    OutboxPollerScheduler(OutboxEventRepository repository, ApplicationEventPublisher applicationEventPublisher) {
        this.repository = repository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT5S")
    @Transactional
    void publishPendingEvents() {
        List<OutboxEvent> pending = repository.findAllByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            applicationEventPublisher.publishEvent(new OutboxEventPublished(
                    event.getId(), event.getAggregateType(), event.getAggregateId(), event.getEventType(), event.getPayload()));
            event.markPublished();
        }
        repository.saveAll(pending);
    }
}
