package com.qrmenu.dailyclose;

import com.qrmenu.ownernotification.OwnerNotificationService;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.BranchBusinessHours;
import com.qrmenu.tenant.TenantService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * product-requirements.md Section 14.2: PREVIEW 10 minutes before closing, FINAL a few
 * minutes after (never exactly at closingTime - the last few minutes of sales would be
 * missed otherwise). Runs every 5 minutes; both triggers use ">=" thresholds rather than
 * a narrow window match, so a missed poll cycle still catches up on the next one instead
 * of silently skipping a branch's close for the day. Branches with no
 * {@link BranchBusinessHours} row for today (or marked closed) are skipped - same
 * "unrestricted" default gap-analysis #4 gave a branch with no configured hours; such a
 * branch can still be closed manually via {@link DailyCloseService#generateFinal}.
 * Each branch is isolated in {@link #generateDueSnapshots} - one branch's failure (bad
 * timezone string, a transient report-generation error, ...) is logged and skipped so
 * every other branch still gets its due PREVIEW/FINAL this poll cycle; generatePreview/
 * generateFinal are independently @Transactional on DailyCloseService, so a failure never
 * rolls back a branch that already committed earlier in the same loop.
 */
@Component
class DailyCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyCloseScheduler.class);
    private static final Duration PREVIEW_LEAD = Duration.ofMinutes(10);
    private static final Duration FINAL_GRACE = Duration.ofMinutes(5);

    private final TenantService tenantService;
    private final DailyCloseService dailyCloseService;
    private final OwnerNotificationService ownerNotificationService;

    DailyCloseScheduler(
            TenantService tenantService,
            DailyCloseService dailyCloseService,
            OwnerNotificationService ownerNotificationService) {
        this.tenantService = tenantService;
        this.dailyCloseService = dailyCloseService;
        this.ownerNotificationService = ownerNotificationService;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    void generateDueSnapshots() {
        Instant now = Instant.now();
        for (Branch branch : tenantService.listAllBranches()) {
            try {
                processBranch(branch, now);
            } catch (Exception e) {
                log.error(
                        "Daily close snapshot generation failed for branch {} (business {}); skipping to the next branch",
                        branch.getId(),
                        branch.getBusinessId(),
                        e);
            }
        }
    }

    private void processBranch(Branch branch, Instant now) {
        ZoneId zone = branch.getTimezone() != null ? ZoneId.of(branch.getTimezone()) : ZoneOffset.UTC;
        ZonedDateTime nowZoned = now.atZone(zone);
        LocalDate businessDate = nowZoned.toLocalDate();
        DayOfWeek dayOfWeek = businessDate.getDayOfWeek();

        Optional<BranchBusinessHours> hours = tenantService
                .getBranchBusinessHours(branch.getBusinessId(), branch.getId())
                .stream()
                .filter(entry -> entry.getDayOfWeek() == dayOfWeek)
                .findFirst();
        if (hours.isEmpty() || hours.get().isClosed() || hours.get().getClosingTime() == null) {
            return;
        }

        Instant closingInstant =
                businessDate.atTime(hours.get().getClosingTime()).atZone(zone).toInstant();
        if (!now.isBefore(closingInstant.minus(PREVIEW_LEAD))) {
            dailyCloseService.generatePreview(branch.getBusinessId(), branch.getId(), businessDate);
        }
        if (!now.isBefore(closingInstant.plus(FINAL_GRACE))) {
            DailyBranchCloseReport report =
                    dailyCloseService.generateFinal(branch.getBusinessId(), branch.getId(), businessDate);
            // Gap-analysis #11 (Section 15): idempotent per (report, contact) inside the service,
            // so re-polling an already-FINAL day here every 5 minutes never double-sends.
            ownerNotificationService.dispatchAutoForDailyClose(report);
        }
    }
}
