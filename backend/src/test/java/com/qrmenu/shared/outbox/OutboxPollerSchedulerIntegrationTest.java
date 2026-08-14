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
 * reliably picked up by the main application's component scan. Not @Transactional at the
 * test-method level: OutboxEventItemPublisher uses REQUIRES_NEW per event so each one
 * commits/rolls back for real (see PaymentTimeoutSchedulerIntegrationTest's class javadoc
 * for why that's incompatible with a shared test transaction).
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

    /**
     * Production-readiness: one listener throwing for a single event must not roll back
     * markPublished() already committed for other events processed earlier in the same
     * poll cycle, and must not stop the remaining pending events from being published.
     * The failing event itself stays unpublished (its own per-event transaction rolls
     * back), so it's retried - the same at-least-once semantics the outbox already relies
     * on, never silently dropped or marked published without actually delivering.
     */
    @Test
    void oneListenerFailureDoesNotBlockOrRollbackOtherPendingEvents() {
        UUID goodAggregateId = UUID.randomUUID();
        UUID failingAggregateId = UUID.randomUUID();
        writer.write("TestAggregate", goodAggregateId, "TestEvent", new TestPayload("good"));
        writer.write("FailingAggregate", failingAggregateId, "TestEvent", new TestPayload("bad"));

        scheduler.publishPendingEvents();

        assertThat(recordingListener.countFor(goodAggregateId)).isEqualTo(1);
        List<OutboxEvent> goodStored = repository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(goodAggregateId))
                .toList();
        assertThat(goodStored).hasSize(1);
        assertThat(goodStored.get(0).getPublishedAt()).isNotNull();

        List<OutboxEvent> failingStored = repository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(failingAggregateId))
                .toList();
        assertThat(failingStored).hasSize(1);
        assertThat(failingStored.get(0).getPublishedAt()).isNull();
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
            if ("FailingAggregate".equals(event.aggregateType())) {
                throw new RuntimeException("Simulated listener failure for " + event.aggregateId());
            }
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
