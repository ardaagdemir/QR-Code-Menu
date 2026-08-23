package com.qrmenu.customersession;

import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.common.web.TableVisitExpiredException;
import com.qrmenu.customersession.repository.AnonymousCustomerSessionRepository;
import com.qrmenu.customersession.repository.TableVisitRepository;
import com.qrmenu.tenant.TableReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerSessionService {

    /**
     * Customer TableVisit security hardening: two independent expiry clocks replacing
     * the old flat VISIT_TTL. INACTIVITY_TIMEOUT resets on every touch() (check-in or
     * order-mutating action); ABSOLUTE_LIFETIME never resets, so a visit can't be kept
     * alive indefinitely just by staying active.
     */
    static final Duration INACTIVITY_TIMEOUT = Duration.ofMinutes(60);

    static final Duration ABSOLUTE_LIFETIME = Duration.ofHours(4);

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
     * existing TableVisit for that session+table (if neither expiry clock has passed) or
     * starts a new one - the exact continuation rule from Section 5. A visit past its
     * inactivity timeout or absolute lifetime is never continued, so re-scanning the QR
     * after either expiry safely starts a fresh visit instead of reviving a stale one.
     */
    @Transactional
    public CheckInResult checkIn(TableReference tableReference, UUID existingSessionId) {
        AnonymousCustomerSession session = existingSessionId != null
                ? sessionRepository.findById(existingSessionId).orElseGet(AnonymousCustomerSession::new)
                : new AnonymousCustomerSession();
        session.touch();
        session = sessionRepository.save(session);

        Instant now = Instant.now();
        UUID sessionId = session.getId();
        TableVisit visit = tableVisitRepository
                .findFirstByAnonymousCustomerSessionIdAndTableIdOrderByStartedAtDesc(sessionId, tableReference.tableId())
                .filter(v -> !v.isExpired(now, INACTIVITY_TIMEOUT, ABSOLUTE_LIFETIME))
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
        if (callerSessionId == null
                || !visit.getAnonymousCustomerSessionId().equals(callerSessionId)
                || visit.isClosed()) {
            throw new ResourceNotFoundException("Table visit not found: " + tableVisitId);
        }
        return visit;
    }

    /**
     * Authoritative, synchronous gate before creating or advancing an order (Section 5:
     * "client state'e güvenme" - never trust a client-supplied notion of the visit still
     * being active). Unlike getOwnedTableVisit's isClosed() check - set asynchronously,
     * up to 15 minutes late, by TableVisitCleanupScheduler - this compares the visit's
     * own timestamps against now() at call time, so an inactivity- or
     * absolute-lifetime-expired visit is rejected immediately even if the scheduler
     * hasn't run yet. A still-active visit is touched here too, extending its inactivity
     * window the same way a QR re-scan would.
     */
    @Transactional
    public TableVisit getActiveTableVisitForOrdering(UUID tableVisitId, UUID callerSessionId) {
        TableVisit visit = getOwnedTableVisit(tableVisitId, callerSessionId);
        if (visit.isExpired(Instant.now(), INACTIVITY_TIMEOUT, ABSOLUTE_LIFETIME)) {
            throw new TableVisitExpiredException("Table visit expired: " + tableVisitId);
        }
        visit.touch();
        return tableVisitRepository.save(visit);
    }

    /**
     * Unlike getOwnedTableVisit, no ownership check: for staff-facing reads (Bölüm 19.3
     * kasa/KDS kartlarındaki masa etiketi) where the caller is already branch-authorized
     * staff, not an anonymous customer session - the tableVisitId isn't the access
     * credential here, branch/permission checks upstream already are.
     */
    @Transactional(readOnly = true)
    public Optional<TableVisit> findTableVisit(UUID tableVisitId) {
        return tableVisitRepository.findById(tableVisitId);
    }

    /** Gap-analysis #7 chain comparison: non-financial table-visit volume per branch since a cutoff. */
    @Transactional(readOnly = true)
    public long countTableVisitsSince(UUID branchId, Instant since) {
        return tableVisitRepository.countByBranchIdAndStartedAtAfter(branchId, since);
    }

    /** Gap-analysis #8 reporting: table-visit volume per branch within a selectable date range. */
    @Transactional(readOnly = true)
    public long countTableVisitsBetween(UUID branchId, Instant from, Instant to) {
        return tableVisitRepository.countByBranchIdAndStartedAtBetween(branchId, from, to);
    }

    /**
     * Gap-analysis #17 (Section 13.3): records/updates/clears the real headcount for a
     * visit the caller owns. Same ownership rule as every other table-visit action - the
     * session cookie is the credential, not the tableVisitId.
     */
    @Transactional
    public TableVisit setGuestCount(UUID tableVisitId, UUID callerSessionId, Integer guestCount) {
        TableVisit visit = getOwnedTableVisit(tableVisitId, callerSessionId);
        visit.setGuestCount(guestCount);
        return tableVisitRepository.save(visit);
    }

    /** Gap-analysis #17 reporting: sum of only the visits where a headcount was actually entered. */
    @Transactional(readOnly = true)
    public long sumGuestCountBetween(UUID branchId, Instant from, Instant to) {
        return tableVisitRepository.sumGuestCountByBranchIdAndStartedAtBetween(branchId, from, to);
    }

    /** Gap-analysis #17 reporting: how many of the range's visits actually recorded a headcount. */
    @Transactional(readOnly = true)
    public long countVisitsWithGuestCountBetween(UUID branchId, Instant from, Instant to) {
        return tableVisitRepository.countByBranchIdAndStartedAtBetweenAndGuestCountIsNotNull(branchId, from, to);
    }
}
