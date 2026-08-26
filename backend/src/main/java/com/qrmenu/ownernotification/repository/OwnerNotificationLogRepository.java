package com.qrmenu.ownernotification.repository;

import com.qrmenu.ownernotification.OwnerNotificationLog;
import com.qrmenu.ownernotification.OwnerNotificationReportType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerNotificationLogRepository extends JpaRepository<OwnerNotificationLog, UUID> {

    List<OwnerNotificationLog> findAllByDailyCloseReportIdOrderByAttemptedAtAsc(UUID dailyCloseReportId);

    boolean existsByDailyCloseReportIdAndBusinessContactId(UUID dailyCloseReportId, UUID businessContactId);

    /** Backoff kontrolü için: bu (branch, ay, kişi) için en son denemenin SENT mi yoksa ne zamanki bir FAILED mi olduğunu bulur. */
    Optional<OwnerNotificationLog> findTopByReportTypeAndBranchIdAndReportPeriodAndBusinessContactIdOrderByAttemptedAtDesc(
            OwnerNotificationReportType reportType, UUID branchId, LocalDate reportPeriod, UUID businessContactId);
}
