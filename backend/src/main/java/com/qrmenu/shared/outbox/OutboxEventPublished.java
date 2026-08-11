package com.qrmenu.shared.outbox;

import java.util.UUID;

/**
 * In-process Spring application event fired by OutboxPollerScheduler once a row has
 * been picked up for publishing. No subscriber exists yet in Milestone 5 (the first
 * consumer - e.g. kitchen reacting to "OrderPaid" - lands in Milestone 6); this type is
 * the seam future modules subscribe to via a plain @EventListener, without ever
 * touching shared.outbox's repository.
 */
public record OutboxEventPublished(UUID outboxEventId, String aggregateType, UUID aggregateId, String eventType, String payload) {
}
