package com.qrmenu.dailyclose;

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
 */
@Component
class DailyCloseScheduler {

    private static final Duration PREVIEW_LEAD = Duration.ofMinutes(10);
    private static final Duration FINAL_GRACE = Duration.ofMinutes(5);

    private final TenantService tenantService;
    private final DailyCloseService dailyCloseService;

    DailyCloseScheduler(TenantService tenantService, DailyCloseService dailyCloseService) {
        this.tenantService = tenantService;
        this.dailyCloseService = dailyCloseService;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    void generateDueSnapshots() {
        Instant now = Instant.now();
        for (Branch branch : tenantService.listAllBranches()) {
            processBranch(branch, now);
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
            dailyCloseService.generateFinal(branch.getBusinessId(), branch.getId(), businessDate);
        }
    }
}
