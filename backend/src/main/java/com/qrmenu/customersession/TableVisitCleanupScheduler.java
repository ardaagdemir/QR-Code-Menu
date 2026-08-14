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
 * TableVisit whose last_activity_at has passed {@link CustomerSessionService#VISIT_TTL}
 * without one ever doing so before. checkIn() already treats a stale visit as over by
 * starting a new one instead of continuing it, but a caller who kept using an old
 * tableVisitId+cookie past the TTL could otherwise still act on it forever via
 * getOwnedTableVisit; this job closes the gap for that path too. Each visit is closed via
 * {@link TableVisitCleanupCloser} and isolated with a try/catch here - one visit that
 * fails to save is logged and skipped instead of rolling back the close() already
 * committed for other visits earlier in the same poll cycle, or blocking the rest of the
 * batch from being closed.
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
        Instant cutoff = Instant.now().minus(CustomerSessionService.VISIT_TTL);
        List<TableVisit> staleVisits = tableVisitRepository.findAllByClosedAtIsNullAndLastActivityAtBefore(cutoff);
        for (TableVisit visit : staleVisits) {
            try {
                tableVisitCleanupCloser.closeVisit(visit.getId());
            } catch (Exception e) {
                log.error("Failed to close stale table visit {}; skipping to the next visit", visit.getId(), e);
            }
        }
    }
}
