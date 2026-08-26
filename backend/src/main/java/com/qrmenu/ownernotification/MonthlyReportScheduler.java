package com.qrmenu.ownernotification;

import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.TenantService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aylık rapor gönderiminin zamanlama tetikleyicisi - {@code DailyCloseScheduler}'ın poll/timezone
 * deseniyle aynı (her branch için ayrı ayrı {@link TenantService#resolveBranchTimeZone}, tek bir
 * branch'in hatası diğerlerini engellemez), ama iş kapanış saatine değil branch'in yerel takvim
 * gününe bağlı: yerel tarih ayın 1'i olduğunda, bir önceki takvim ayı için {@link
 * OwnerNotificationService#dispatchAutoForMonthlyReport} tetiklenir. Asıl idempotency/backoff
 * {@link MonthlyReportContactDispatcher} içinde olduğu için, gün boyunca (ayın 1'i süresince) her
 * 5 dakikada bir tekrar tetiklenmesi zararsız - zaten gönderilmiş kişiler atlanır.
 */
@Component
class MonthlyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportScheduler.class);

    private final TenantService tenantService;
    private final OwnerNotificationService ownerNotificationService;

    MonthlyReportScheduler(TenantService tenantService, OwnerNotificationService ownerNotificationService) {
        this.tenantService = tenantService;
        this.ownerNotificationService = ownerNotificationService;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    void generateDueMonthlyReports() {
        run(Instant.now());
    }

    /** Package-private Instant overload so tests can drive "ayın 1'i" without waiting for the real calendar. */
    void run(Instant now) {
        for (Branch branch : tenantService.listAllBranches()) {
            try {
                processBranch(branch, now);
            } catch (Exception e) {
                log.error(
                        "Monthly report dispatch failed for branch {} (business {}); skipping to the next branch",
                        branch.getId(),
                        branch.getBusinessId(),
                        e);
            }
        }
    }

    private void processBranch(Branch branch, Instant now) {
        ZoneId zone = tenantService.resolveBranchTimeZone(branch);
        ZonedDateTime nowZoned = now.atZone(zone);
        if (nowZoned.getDayOfMonth() != 1) {
            return;
        }
        YearMonth periodMonth = YearMonth.from(nowZoned).minusMonths(1);
        ownerNotificationService.dispatchAutoForMonthlyReport(branch.getBusinessId(), branch.getId(), periodMonth);
    }
}
