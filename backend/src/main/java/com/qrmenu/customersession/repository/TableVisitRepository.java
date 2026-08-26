package com.qrmenu.customersession.repository;

import com.qrmenu.customersession.TableVisit;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableVisitRepository extends JpaRepository<TableVisit, UUID> {

    Optional<TableVisit> findFirstByAnonymousCustomerSessionIdAndTableIdOrderByStartedAtDesc(
            UUID anonymousCustomerSessionId, UUID tableId);

    /** Masa yaşam döngüsü: hard-delete yalnızca hiç TableVisit geçmişi olmayan masa için mümkün. */
    boolean existsByTableId(UUID tableId);

    /** Masa yaşam döngüsü: archive gate - açık (closedAt IS NULL) ziyaretler, expiry kontrolü çağıran tarafta (isExpired). */
    List<TableVisit> findAllByTableIdAndClosedAtIsNull(UUID tableId);

    /** Masa yaşam döngüsü: bu masaya ait tüm ziyaretlerin id'leri - OrderingService'in aktif sipariş kontrolü için. */
    @Query("SELECT t.id FROM TableVisit t WHERE t.tableId = :tableId")
    List<UUID> findIdsByTableId(@Param("tableId") UUID tableId);

    /**
     * Gap-analysis #13: visits still open despite having passed either expiry clock -
     * inactivity (lastActivityAt) or absolute lifetime (startedAt) - for the closing
     * scheduler.
     */
    @Query("SELECT t FROM TableVisit t WHERE t.closedAt IS NULL "
            + "AND (t.lastActivityAt < :inactivityCutoff OR t.startedAt < :absoluteLifetimeCutoff)")
    List<TableVisit> findAllExpiredAndOpen(
            @Param("inactivityCutoff") Instant inactivityCutoff, @Param("absoluteLifetimeCutoff") Instant absoluteLifetimeCutoff);

    /** Gap-analysis #7 chain comparison: table-visit volume per branch since a fixed cutoff. */
    long countByBranchIdAndStartedAtAfter(UUID branchId, Instant since);

    /** Gap-analysis #8 reporting: table-visit volume per branch within a selectable date range. */
    long countByBranchIdAndStartedAtBetween(UUID branchId, Instant from, Instant to);

    /** Gap-analysis #17 reporting: how many visits in range actually recorded a headcount. */
    long countByBranchIdAndStartedAtBetweenAndGuestCountIsNotNull(UUID branchId, Instant from, Instant to);

    /** Gap-analysis #17 reporting: sum of only the visits where a headcount was actually entered - never defaults unset visits to 0 or 1. */
    @Query("SELECT COALESCE(SUM(t.guestCount), 0) FROM TableVisit t "
            + "WHERE t.branchId = :branchId AND t.startedAt BETWEEN :from AND :to AND t.guestCount IS NOT NULL")
    long sumGuestCountByBranchIdAndStartedAtBetween(
            @Param("branchId") UUID branchId, @Param("from") Instant from, @Param("to") Instant to);
}
