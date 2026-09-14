package com.qrmenu.ownernotification.web;

import com.qrmenu.dailyclose.DailyCloseService;
import com.qrmenu.ownernotification.OwnerNotificationLog;
import com.qrmenu.ownernotification.OwnerNotificationReportType;
import com.qrmenu.ownernotification.OwnerNotificationService;
import com.qrmenu.ownernotification.web.dto.ReportNotificationResponse;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raporlar ekranındaki "Rapor Bildirimleri" bölümü: DAILY (Gün Sonu Kapanışları'ndaki mevcut
 * per-report resend akışına dokunmadan) ve MONTHLY {@link OwnerNotificationLog} geçmişini tek
 * listede birleştirir, ve MONTHLY için ilk kez bir manuel resend akışı sağlar - {@link
 * OwnerNotificationService#resendMonthly}, DAILY'nin {@code resend}'i gibi AUTO idempotency'yi
 * bypass eder ama MonthlyReportContactDispatcher'ın advisory-lock/backoff kurallarını hiç görmez.
 * REPORT_VIEW ile aynı yetki seviyesi - {@code StaffDailyCloseController}'ın notification
 * endpoint'leriyle tutarlı (CASHIER/BRANCH_MANAGER/BUSINESS_ADMIN hepsi resend tetikleyebilir).
 */
@RestController
public class StaffReportNotificationController {

    private final OwnerNotificationService ownerNotificationService;
    private final DailyCloseService dailyCloseService;
    private final StaffAuthService staffAuthService;

    public StaffReportNotificationController(
            OwnerNotificationService ownerNotificationService,
            DailyCloseService dailyCloseService,
            StaffAuthService staffAuthService) {
        this.ownerNotificationService = ownerNotificationService;
        this.dailyCloseService = dailyCloseService;
        this.staffAuthService = staffAuthService;
    }

    @GetMapping({"/api/staff/reports/notifications", "/api/staff/branches/{branchId}/reports/notifications"})
    public List<ReportNotificationResponse> listNotifications(
            @PathVariable(required = false) UUID branchId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = resolveContext(sessionCookie, branchId);
        UUID resolvedBranchId = context.activeBranchId();
        List<OwnerNotificationLog> logs = ownerNotificationService.listRecentForBranch(resolvedBranchId);
        Map<UUID, LocalDate> dailyBusinessDates = dailyCloseService.getBusinessDatesByIds(
                logs.stream()
                        .filter(entry -> entry.getReportType() == OwnerNotificationReportType.DAILY)
                        .map(OwnerNotificationLog::getDailyCloseReportId)
                        .collect(Collectors.toSet()));
        return logs.stream().map(entry -> toResponse(entry, dailyBusinessDates)).toList();
    }

    /** FAILED bir aylık gönderimde "Yeniden Dene", SENT bir aylık gönderimde "Tekrar Gönder" - ikisi de bu akışı çağırır. */
    @PostMapping({"/api/staff/reports/monthly/resend", "/api/staff/branches/{branchId}/reports/monthly/resend"})
    public List<ReportNotificationResponse> resendMonthly(
            @PathVariable(required = false) UUID branchId,
            @RequestParam String period,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = resolveContext(sessionCookie, branchId);
        UUID resolvedBranchId = context.activeBranchId();
        YearMonth periodMonth = parsePeriod(period);
        List<OwnerNotificationLog> logs = ownerNotificationService.resendMonthly(
                context.businessId(), resolvedBranchId, periodMonth, context.staffUserId());
        return logs.stream().map(entry -> toResponse(entry, Map.of())).toList();
    }

    private StaffContext resolveContext(String sessionCookie, UUID requestedBranchId) {
        UUID sessionId = StaffCookieSupport.parseSessionId(sessionCookie);
        return requestedBranchId == null
                ? staffAuthService.resolveStaffContextForActiveBranch(sessionId, Permission.REPORT_VIEW)
                : staffAuthService.resolveStaffContextForBranch(sessionId, Permission.REPORT_VIEW, requestedBranchId);
    }

    private static YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid period: " + period);
        }
    }

    private static ReportNotificationResponse toResponse(
            OwnerNotificationLog log, Map<UUID, LocalDate> dailyBusinessDates) {
        LocalDate period = log.getReportType() == OwnerNotificationReportType.MONTHLY
                ? log.getReportPeriod()
                : dailyBusinessDates.get(log.getDailyCloseReportId());
        return new ReportNotificationResponse(
                log.getId(),
                log.getReportType().name(),
                log.getDailyCloseReportId(),
                period,
                log.getRecipientEmail(),
                log.getStatus().name(),
                log.getErrorMessage(),
                log.getTriggeredBy().name(),
                log.getAttemptedAt());
    }
}
