package com.qrmenu.dailyclose.web;

import com.qrmenu.dailyclose.DailyBranchCloseReport;
import com.qrmenu.dailyclose.DailyCloseExcelExportService;
import com.qrmenu.dailyclose.DailyCloseService;
import com.qrmenu.dailyclose.web.dto.DailyCloseReportResponse;
import com.qrmenu.ownernotification.OwnerNotificationLog;
import com.qrmenu.ownernotification.OwnerNotificationService;
import com.qrmenu.ownernotification.web.dto.OwnerNotificationLogResponse;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.TenantService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gap-analysis #9 (product-requirements.md Section 14): gün sonu kapanış snapshot'ları ve Excel export. */
@RestController
public class StaffDailyCloseController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DailyCloseService dailyCloseService;
    private final DailyCloseExcelExportService excelExportService;
    private final TenantService tenantService;
    private final StaffAuthService staffAuthService;
    private final OwnerNotificationService ownerNotificationService;

    public StaffDailyCloseController(
            DailyCloseService dailyCloseService,
            DailyCloseExcelExportService excelExportService,
            TenantService tenantService,
            StaffAuthService staffAuthService,
            OwnerNotificationService ownerNotificationService) {
        this.dailyCloseService = dailyCloseService;
        this.excelExportService = excelExportService;
        this.tenantService = tenantService;
        this.staffAuthService = staffAuthService;
        this.ownerNotificationService = ownerNotificationService;
    }

    @GetMapping("/api/staff/branches/{branchId}/daily-close")
    public List<DailyCloseReportResponse> listBranchCloseReports(
            @PathVariable UUID branchId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContextForBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.REPORT_VIEW, branchId);
        String branchName = tenantService.getBranch(context.businessId(), branchId).getName();
        return dailyCloseService.listForBranch(branchId, from, to).stream()
                .map(report -> toResponse(report, branchName))
                .toList();
    }

    /** Section 14.1/14.2: personel, otomatik zamanlama beklemeden bugünü elle kapatabilir (ör. hatalı business hours). */
    @PostMapping("/api/staff/branches/{branchId}/daily-close/final")
    public DailyCloseReportResponse generateFinal(
            @PathVariable UUID branchId,
            @RequestParam LocalDate businessDate,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContextForBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.REPORT_VIEW, branchId);
        DailyBranchCloseReport report = dailyCloseService.generateFinal(context.businessId(), branchId, businessDate);
        String branchName = tenantService.getBranch(context.businessId(), branchId).getName();
        return toResponse(report, branchName);
    }

    @GetMapping("/api/staff/branches/{branchId}/daily-close/excel")
    public ResponseEntity<byte[]> exportBranchExcel(
            @PathVariable UUID branchId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContextForBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.REPORT_VIEW, branchId);
        Branch branch = tenantService.getBranch(context.businessId(), branchId);
        List<DailyBranchCloseReport> rows = dailyCloseService.listForBranch(branchId, from, to);
        byte[] workbook = excelExportService.export(rows, Map.of(branchId, branch.getName()));
        return excelResponse(workbook, "gun-sonu-" + branch.getName() + "-" + from + "_" + to + ".xlsx");
    }

    /** Section 13.2 ile aynı BUSINESS_ADMIN/PLATFORM_ADMIN-only zincir görünürlüğü. */
    /** Gap-analysis #11 (Section 15): hangi rapor kime, ne zaman, hangi durumda gönderildi. */
    @GetMapping("/api/staff/branches/{branchId}/daily-close/{reportId}/notifications")
    public List<OwnerNotificationLogResponse> listNotifications(
            @PathVariable UUID branchId,
            @PathVariable UUID reportId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContextForBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.REPORT_VIEW, branchId);
        dailyCloseService.getById(context.businessId(), branchId, reportId);
        return ownerNotificationService.listForReport(reportId).stream()
                .map(StaffDailyCloseController::toNotificationResponse)
                .toList();
    }

    /** Manuel yeniden gönderme - AUTO idempotency kontrolünü atlar, uygun her alıcıya yeni bir deneme yazar. */
    @PostMapping("/api/staff/branches/{branchId}/daily-close/{reportId}/notifications/resend")
    public List<OwnerNotificationLogResponse> resendNotifications(
            @PathVariable UUID branchId,
            @PathVariable UUID reportId,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContextForBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.REPORT_VIEW, branchId);
        DailyBranchCloseReport report = dailyCloseService.getById(context.businessId(), branchId, reportId);
        return ownerNotificationService.resend(report, context.staffUserId()).stream()
                .map(StaffDailyCloseController::toNotificationResponse)
                .toList();
    }

    @GetMapping("/api/staff/daily-close/excel")
    public ResponseEntity<byte[]> exportChainExcel(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContext(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.REPORT_CHAIN_VIEW);
        List<Branch> branches = tenantService.listBranches(context.businessId());
        Map<UUID, String> branchNames = branches.stream().collect(Collectors.toMap(Branch::getId, Branch::getName));
        List<DailyBranchCloseReport> rows = dailyCloseService.listForBusiness(context.businessId(), from, to);
        byte[] workbook = excelExportService.export(rows, branchNames);
        return excelResponse(workbook, "gun-sonu-zincir-" + from + "_" + to + ".xlsx");
    }

    private static ResponseEntity<byte[]> excelResponse(byte[] workbook, String filename) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(workbook);
    }

    private static OwnerNotificationLogResponse toNotificationResponse(OwnerNotificationLog log) {
        return new OwnerNotificationLogResponse(
                log.getId(),
                log.getRecipientEmail(),
                log.getChannel().name(),
                log.getStatus().name(),
                log.getErrorMessage(),
                log.getTriggeredBy().name(),
                log.getAttemptedAt());
    }

    private static DailyCloseReportResponse toResponse(DailyBranchCloseReport report, String branchName) {
        return new DailyCloseReportResponse(
                report.getId(),
                report.getBranchId(),
                branchName,
                report.getBusinessDate(),
                report.getPeriodStart(),
                report.getPeriodEnd(),
                report.getGrossSalesMinorUnits(),
                report.getRefundTotalMinorUnits(),
                report.getNetSalesMinorUnits(),
                report.getOrderCount(),
                report.getAcceptedOrderCount(),
                report.getRejectedOrderCount(),
                report.getAverageOrderValueMinorUnits(),
                report.getTableVisitCount(),
                report.getStatus().name(),
                report.getGeneratedAt());
    }
}
