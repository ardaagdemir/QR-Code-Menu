package com.qrmenu.ownernotification;

import com.qrmenu.dailyclose.DailyBranchCloseReport;
import com.qrmenu.ownernotification.repository.OwnerNotificationLogRepository;
import com.qrmenu.reporting.BranchSalesReportView;
import com.qrmenu.reporting.ProductSalesView;
import com.qrmenu.reporting.ReportingService;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.Business;
import com.qrmenu.tenant.BusinessContact;
import com.qrmenu.tenant.TenantService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap-analysis #11 (product-requirements.md Section 15): dispatches the FINAL daily close
 * summary to every {@link BusinessContact} opted into daily reports. {@link
 * #dispatchAutoForDailyClose} is called (as a separate-bean {@code @Async} method, so no
 * self-invocation pitfall - see {@code MockPaymentSimulationDispatcher}'s note) right after
 * {@code DailyCloseScheduler} generates a FINAL report; it's idempotent per (report, contact)
 * so the scheduler's every-5-minutes re-poll of an already-closed day never double-sends.
 * {@link #resend} is the synchronous manual counterpart - it bypasses that idempotency guard
 * on purpose, so staff can retry after fixing a bad BusinessContact email.
 */
@Service
public class OwnerNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OwnerNotificationService.class);
    private static final int TOP_PRODUCTS_LIMIT = 5;

    private final TenantService tenantService;
    private final ReportingService reportingService;
    private final OwnerNotificationLogRepository repository;
    private final OwnerNotificationPort emailPort;

    public OwnerNotificationService(
            TenantService tenantService,
            ReportingService reportingService,
            OwnerNotificationLogRepository repository,
            OwnerNotificationPort emailPort) {
        this.tenantService = tenantService;
        this.reportingService = reportingService;
        this.repository = repository;
        this.emailPort = emailPort;
    }

    /**
     * Returns a future purely so tests can deterministically wait for the async dispatch to
     * finish - {@link com.qrmenu.dailyclose.DailyCloseScheduler} (the only production caller)
     * ignores it, it's genuinely fire-and-forget there.
     */
    @Async
    public CompletableFuture<Void> dispatchAutoForDailyClose(DailyBranchCloseReport report) {
        dispatch(report, OwnerNotificationTrigger.AUTO, null);
        return CompletableFuture.completedFuture(null);
    }

    public List<OwnerNotificationLog> resend(DailyBranchCloseReport report, UUID actorStaffUserId) {
        dispatch(report, OwnerNotificationTrigger.MANUAL, actorStaffUserId);
        return listForReport(report.getId());
    }

    @Transactional(readOnly = true)
    public List<OwnerNotificationLog> listForReport(UUID dailyCloseReportId) {
        return repository.findAllByDailyCloseReportIdOrderByAttemptedAtAsc(dailyCloseReportId);
    }

    private void dispatch(DailyBranchCloseReport report, OwnerNotificationTrigger trigger, UUID actorStaffUserId) {
        List<BusinessContact> recipients = tenantService.listBusinessContacts(report.getBusinessId()).stream()
                .filter(BusinessContact::isActive)
                .filter(BusinessContact::isDailyReportRecipient)
                .filter(contact -> contact.getEmail() != null && !contact.getEmail().isBlank())
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        Branch branch = tenantService.getBranch(report.getBusinessId(), report.getBranchId());
        Business business = tenantService.getBusiness(report.getBusinessId());
        String body = buildBody(report, branch, business);
        String subject = "Gün Sonu Raporu - " + branch.getName() + " - " + report.getBusinessDate();

        for (BusinessContact contact : recipients) {
            if (trigger == OwnerNotificationTrigger.AUTO
                    && repository.existsByDailyCloseReportIdAndBusinessContactId(report.getId(), contact.getId())) {
                continue;
            }
            attemptDelivery(report, contact, subject, body, trigger, actorStaffUserId);
        }
    }

    private void attemptDelivery(
            DailyBranchCloseReport report,
            BusinessContact contact,
            String subject,
            String body,
            OwnerNotificationTrigger trigger,
            UUID actorStaffUserId) {
        OwnerNotificationStatus status;
        String errorMessage = null;
        try {
            emailPort.send(new OwnerNotificationMessage(contact.getEmail(), subject, body));
            status = OwnerNotificationStatus.SENT;
        } catch (OwnerNotificationDeliveryException e) {
            log.warn(
                    "Gün sonu bildirimi gönderilemedi (reportId={}, contactId={}): {}",
                    report.getId(),
                    contact.getId(),
                    e.getMessage());
            status = OwnerNotificationStatus.FAILED;
            errorMessage = e.getMessage();
        }
        saveLog(report, contact, status, errorMessage, trigger, actorStaffUserId);
    }

    private void saveLog(
            DailyBranchCloseReport report,
            BusinessContact contact,
            OwnerNotificationStatus status,
            String errorMessage,
            OwnerNotificationTrigger trigger,
            UUID actorStaffUserId) {
        repository.save(new OwnerNotificationLog(
                report.getId(),
                report.getBusinessId(),
                report.getBranchId(),
                contact.getId(),
                contact.getEmail(),
                OwnerNotificationChannel.EMAIL,
                status,
                errorMessage,
                trigger,
                actorStaffUserId,
                Instant.now()));
    }

    private String buildBody(DailyBranchCloseReport report, Branch branch, Business business) {
        BranchSalesReportView salesView = reportingService.getBranchReport(
                report.getBusinessId(), report.getBranchId(), report.getBusinessDate(), report.getBusinessDate());
        String currency = business.getDefaultCurrency();

        StringBuilder sb = new StringBuilder();
        sb.append("Şube: ").append(branch.getName()).append('\n');
        sb.append("Tarih: ").append(report.getBusinessDate()).append('\n');
        sb.append('\n');
        sb.append("Brüt satış: ").append(formatMoney(report.getGrossSalesMinorUnits(), currency)).append('\n');
        sb.append("Net satış: ").append(formatMoney(report.getNetSalesMinorUnits(), currency)).append('\n');
        sb.append("İade: ").append(formatMoney(report.getRefundTotalMinorUnits(), currency)).append('\n');
        sb.append("Sipariş sayısı: ").append(report.getOrderCount()).append('\n');
        sb.append('\n');

        List<ProductSalesView> topProducts =
                salesView.productBreakdown().stream().limit(TOP_PRODUCTS_LIMIT).toList();
        if (topProducts.isEmpty()) {
            sb.append("En çok satan ürün: kayıt yok\n");
        } else {
            sb.append("En çok satan ürünler:\n");
            for (ProductSalesView p : topProducts) {
                sb.append("- ")
                        .append(p.productName())
                        .append(" (")
                        .append(p.quantitySold())
                        .append(" adet, ")
                        .append(formatMoney(p.revenueMinorUnits(), currency))
                        .append(")\n");
            }
        }
        return sb.toString();
    }

    private static String formatMoney(long minorUnits, String currency) {
        return String.format("%.2f %s", minorUnits / 100.0, currency);
    }
}
