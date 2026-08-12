package com.qrmenu.customersession;

import com.qrmenu.customersession.repository.TableVisitRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap-analysis #13 (docs/gap-analysis.md Section 2 "Session/TableVisit TTL"): closes any
 * TableVisit whose last_activity_at has passed {@link CustomerSessionService#VISIT_TTL}
 * without one ever doing so before. checkIn() already treats a stale visit as over by
 * starting a new one instead of continuing it, but a caller who kept using an old
 * tableVisitId+cookie past the TTL could otherwise still act on it forever via
 * getOwnedTableVisit; this job closes the gap for that path too.
 */
@Component
class TableVisitCleanupScheduler {

    private final TableVisitRepository tableVisitRepository;

    TableVisitCleanupScheduler(TableVisitRepository tableVisitRepository) {
        this.tableVisitRepository = tableVisitRepository;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @Transactional
    void closeStaleTableVisits() {
        Instant cutoff = Instant.now().minus(CustomerSessionService.VISIT_TTL);
        List<TableVisit> staleVisits = tableVisitRepository.findAllByClosedAtIsNullAndLastActivityAtBefore(cutoff);
        staleVisits.forEach(TableVisit::close);
        tableVisitRepository.saveAll(staleVisits);
    }
}
