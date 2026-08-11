package com.qrmenu.customersession;

import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.customersession.repository.AnonymousCustomerSessionRepository;
import com.qrmenu.customersession.repository.TableVisitRepository;
import com.qrmenu.tenant.TableReference;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerSessionService {

    /**
     * RECOMMENDED in docs/product-requirements.md Section 5 (not a fixed user
     * decision): "TableVisit için ~4-6 saat". Picking the upper bound of that range.
     */
    static final Duration VISIT_TTL = Duration.ofHours(6);

    private final AnonymousCustomerSessionRepository sessionRepository;
    private final TableVisitRepository tableVisitRepository;

    public CustomerSessionService(
            AnonymousCustomerSessionRepository sessionRepository, TableVisitRepository tableVisitRepository) {
        this.sessionRepository = sessionRepository;
        this.tableVisitRepository = tableVisitRepository;
    }

    /**
     * Resumes the caller's AnonymousCustomerSession (creating one if the cookie is
     * missing or points at a session that no longer exists), then either continues the
     * existing TableVisit for that session+table (if still within VISIT_TTL) or starts
     * a new one - the exact continuation rule from Section 5.
     */
    @Transactional
    public CheckInResult checkIn(TableReference tableReference, UUID existingSessionId) {
        AnonymousCustomerSession session = existingSessionId != null
                ? sessionRepository.findById(existingSessionId).orElseGet(AnonymousCustomerSession::new)
                : new AnonymousCustomerSession();
        session.touch();
        session = sessionRepository.save(session);

        Instant cutoff = Instant.now().minus(VISIT_TTL);
        UUID sessionId = session.getId();
        TableVisit visit = tableVisitRepository
                .findFirstByAnonymousCustomerSessionIdAndTableIdOrderByStartedAtDesc(sessionId, tableReference.tableId())
                .filter(v -> v.getLastActivityAt().isAfter(cutoff))
                .map(v -> {
                    v.touch();
                    return v;
                })
                .orElseGet(() -> new TableVisit(
                        tableReference.businessId(), tableReference.branchId(), tableReference.tableId(), sessionId));
        visit = tableVisitRepository.save(visit);

        return new CheckInResult(sessionId, visit);
    }

    /**
     * Used by other modules (e.g. ordering's cart) that need to act on a TableVisit but
     * must first prove the caller actually owns it - the AnonymousCustomerSession cookie
     * is the access credential for everything derived from a visit (Section 5), not the
     * tableVisitId itself (a UUID in a URL is not a secret). A missing/invalid cookie or
     * an owner mismatch both surface as 404, not 403, to avoid confirming the visit's
     * existence to a caller who doesn't hold its session.
     */
    @Transactional(readOnly = true)
    public TableVisit getOwnedTableVisit(UUID tableVisitId, UUID callerSessionId) {
        TableVisit visit = tableVisitRepository
                .findById(tableVisitId)
                .orElseThrow(() -> new ResourceNotFoundException("Table visit not found: " + tableVisitId));
        if (callerSessionId == null || !visit.getAnonymousCustomerSessionId().equals(callerSessionId)) {
            throw new ResourceNotFoundException("Table visit not found: " + tableVisitId);
        }
        return visit;
    }

    /** Gap-analysis #7 chain comparison: non-financial table-visit volume per branch since a cutoff. */
    @Transactional(readOnly = true)
    public long countTableVisitsSince(UUID branchId, Instant since) {
        return tableVisitRepository.countByBranchIdAndStartedAtAfter(branchId, since);
    }
}
