package com.qrmenu.customersession;

import com.qrmenu.customersession.repository.TableVisitRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gap-analysis #13 (docs/gap-analysis.md Section 2 "Session/TableVisit TTL"): closes any
 * TableVisit that has passed its {@link CustomerSessionService#INACTIVITY_TIMEOUT} or its
 * {@link CustomerSessionService#ABSOLUTE_LIFETIME} without one ever doing so before.
 * checkIn() already treats such a visit as over by starting a new one instead of
 * continuing it, and OrderingService's order-mutating calls reject it synchronously via
 * CustomerSessionService.getActiveTableVisitForOrdering regardless of this job - this
 * scheduler only closes the gap for callers who keep polling a read-only endpoint
 * (getOwnedTableVisit) on an old tableVisitId+cookie past both clocks. Each visit is
 * closed via {@link TableVisitCleanupCloser} and isolated with a try/catch here - one
 * visit that fails to save is logged and skipped instead of rolling back the close()
 * already committed for other visits earlier in the same poll cycle, or blocking the
 * rest of the batch from being closed.
 */
@Component
class TableVisitCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TableVisitCleanupScheduler.class);

    private final TableVisitRepository tableVisitRepository;
    private final TableVisitCleanupCloser tableVisitCleanupCloser;

    TableVisitCleanupScheduler(TableVisitRepository tableVisitRepository, TableVisitCleanupCloser tableVisitCleanupCloser) {
        this.tableVisitRepository = tableVisitRepository;
        this.tableVisitCleanupCloser = tableVisitCleanupCloser;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    void closeStaleTableVisits() {
        Instant now = Instant.now();
        Instant inactivityCutoff = now.minus(CustomerSessionService.INACTIVITY_TIMEOUT);
        Instant absoluteLifetimeCutoff = now.minus(CustomerSessionService.ABSOLUTE_LIFETIME);
        List<TableVisit> staleVisits = tableVisitRepository.findAllExpiredAndOpen(inactivityCutoff, absoluteLifetimeCutoff);
        for (TableVisit visit : staleVisits) {
            try {
                tableVisitCleanupCloser.closeVisit(visit.getId());
            } catch (Exception e) {
                log.error("Failed to close stale table visit {}; skipping to the next visit", visit.getId(), e);
            }
        }
    }
}
