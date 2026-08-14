package com.qrmenu.customersession;

import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.customersession.repository.TableVisitRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes exactly one stale TableVisit per call, in its own REQUIRES_NEW transaction
 * (same reasoning as PaymentWebhookIdempotencyGuard) - a separate bean, not a method
 * called via `this.` on TableVisitCleanupScheduler, since self-invocation bypasses the
 * Spring proxy entirely and @Transactional would silently not apply. REQUIRES_NEW (not
 * just a plain @Transactional) means this visit's own commit or rollback is final and
 * immediate, not deferred to whatever ambient transaction happens to be active - one
 * visit failing to save can't roll back or block visits already processed earlier in the
 * same poll cycle.
 */
@Component
class TableVisitCleanupCloser {

    private final TableVisitRepository tableVisitRepository;

    TableVisitCleanupCloser(TableVisitRepository tableVisitRepository) {
        this.tableVisitRepository = tableVisitRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void closeVisit(UUID tableVisitId) {
        TableVisit visit = tableVisitRepository
                .findById(tableVisitId)
                .orElseThrow(() -> new ResourceNotFoundException("TableVisit not found: " + tableVisitId));
        visit.close();
        tableVisitRepository.save(visit);
    }
}
