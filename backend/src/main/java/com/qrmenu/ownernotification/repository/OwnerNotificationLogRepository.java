package com.qrmenu.ownernotification.repository;

import com.qrmenu.ownernotification.OwnerNotificationLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerNotificationLogRepository extends JpaRepository<OwnerNotificationLog, UUID> {

    List<OwnerNotificationLog> findAllByDailyCloseReportIdOrderByAttemptedAtAsc(UUID dailyCloseReportId);

    boolean existsByDailyCloseReportIdAndBusinessContactId(UUID dailyCloseReportId, UUID businessContactId);
}
