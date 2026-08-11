package com.qrmenu.shared.outbox;

import com.qrmenu.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the outbox infrastructure itself (Section 9, Milestone 5): a written-but-not-
 * yet-published row is picked up by the poller, republished as an in-process
 * OutboxEventPublished application event, and marked published so a second run does
 * not republish it. Invokes the scheduled method directly (same pattern as
 * OrderCleanupSchedulerIntegrationTest) rather than waiting for its real 10s trigger -
 * the real @Scheduled timer is also active in the background for the whole shared test
 * context, so RecordingListener filters by this test's own random aggregateId to stay
 * isolated from that noise. RecordingListener is registered via @TestConfiguration/
 * @Import (not @Component) - a plain @Component on a static nested test class is not
 * reliably picked up by the main application's component scan.
 */
@Import(OutboxPollerSchedulerIntegrationTest.RecordingListenerConfig.class)
class OutboxPollerSchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventWriter writer;

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxPollerScheduler scheduler;

    @Autowired
    private RecordingListener recordingListener;

    @Test
    @Transactional
    void pendingEventsArePublishedExactlyOnce() {
        UUID aggregateId = UUID.randomUUID();
        writer.write("TestAggregate", aggregateId, "TestEvent", new TestPayload("hello"));

        scheduler.publishPendingEvents();

        List<OutboxEvent> stored = repository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(aggregateId))
                .toList();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getPublishedAt()).isNotNull();
        assertThat(recordingListener.countFor(aggregateId)).isEqualTo(1);

        // A second run must not republish an already-published row.
        scheduler.publishPendingEvents();
        assertThat(recordingListener.countFor(aggregateId)).isEqualTo(1);
    }

    private record TestPayload(String value) {
    }

    @TestConfiguration
    static class RecordingListenerConfig {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {
        private final List<OutboxEventPublished> received = new CopyOnWriteArrayList<>();

        @EventListener
        void onOutboxEventPublished(OutboxEventPublished event) {
            received.add(event);
        }

        int countFor(UUID aggregateId) {
            AtomicInteger count = new AtomicInteger();
            received.forEach(event -> {
                if (event.aggregateId().equals(aggregateId)) {
                    count.incrementAndGet();
                }
            });
            return count.get();
        }
    }
}
