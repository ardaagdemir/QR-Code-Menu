package com.qrmenu.dailyclose;

import com.qrmenu.dailyclose.repository.DailyCloseReportRepository;
import com.qrmenu.reporting.BranchSalesReportView;
import com.qrmenu.reporting.ReportingService;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.TenantService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap-analysis #9 (product-requirements.md Section 14.1): daily branch close snapshots,
 * fed by {@link ReportingService#getBranchReport} (gap-analysis #8) for a single
 * business-date so the two never disagree on how gross/net/refund are computed.
 * PREVIEW rows recompute on every call; FINAL is immutable once created - a second
 * generateFinal for the same branch+date just returns the existing row, it never
 * overwrites (Section 14.3: Excel export always regenerates from these rows, so a FINAL
 * row must stay the number that was actually reported).
 */
@Service
public class DailyCloseService {

    private final ReportingService reportingService;
    private final TenantService tenantService;
    private final DailyCloseReportRepository repository;

    public DailyCloseService(
            ReportingService reportingService, TenantService tenantService, DailyCloseReportRepository repository) {
        this.reportingService = reportingService;
        this.tenantService = tenantService;
        this.repository = repository;
    }

    @Transactional
    public DailyBranchCloseReport generatePreview(UUID businessId, UUID branchId, LocalDate businessDate) {
        Optional<DailyBranchCloseReport> existing = repository.findByBranchIdAndBusinessDate(branchId, businessDate);
        if (existing.isPresent() && existing.get().getStatus() == DailyCloseStatus.FINAL) {
            return existing.get();
        }

        Branch branch = tenantService.getBranch(businessId, branchId);
        BranchSalesReportView view = reportingService.getBranchReport(businessId, branchId, businessDate, businessDate);
        ZoneId zone = resolveZone(branch);
        Instant periodStart = businessDate.atStartOfDay(zone).toInstant();
        Instant periodEnd = businessDate.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = Instant.now();

        if (existing.isPresent()) {
            DailyBranchCloseReport report = existing.get();
            report.applyPreview(
                    periodStart,
                    periodEnd,
                    view.grossSalesMinorUnits(),
                    view.refundTotalMinorUnits(),
                    view.netSalesMinorUnits(),
                    view.orderCount(),
                    view.acceptedOrderCount(),
                    view.rejectedOrderCount(),
                    view.averageOrderValueMinorUnits(),
                    view.tableVisitCount(),
                    now);
            return repository.save(report);
        }

        DailyBranchCloseReport report = new DailyBranchCloseReport(
                businessId,
                branchId,
                businessDate,
                periodStart,
                periodEnd,
                view.grossSalesMinorUnits(),
                view.refundTotalMinorUnits(),
                view.netSalesMinorUnits(),
                view.orderCount(),
                view.acceptedOrderCount(),
                view.rejectedOrderCount(),
                view.averageOrderValueMinorUnits(),
                view.tableVisitCount(),
                DailyCloseStatus.PREVIEW,
                now);
        return repository.save(report);
    }

    @Transactional
    public DailyBranchCloseReport generateFinal(UUID businessId, UUID branchId, LocalDate businessDate) {
        Optional<DailyBranchCloseReport> existing = repository.findByBranchIdAndBusinessDate(branchId, businessDate);
        if (existing.isPresent() && existing.get().getStatus() == DailyCloseStatus.FINAL) {
            return existing.get();
        }
        DailyBranchCloseReport report = generatePreview(businessId, branchId, businessDate);
        report.markFinal(Instant.now());
        return repository.save(report);
    }

    @Transactional(readOnly = true)
    public List<DailyBranchCloseReport> listForBranch(UUID branchId, LocalDate from, LocalDate to) {
        return repository.findAllByBranchIdAndBusinessDateBetweenOrderByBusinessDateAsc(branchId, from, to);
    }

    @Transactional(readOnly = true)
    public List<DailyBranchCloseReport> listForBusiness(UUID businessId, LocalDate from, LocalDate to) {
        return repository.findAllByBusinessIdAndBusinessDateBetweenOrderByBranchIdAscBusinessDateAsc(businessId, from, to);
    }

    private static ZoneId resolveZone(Branch branch) {
        return branch.getTimezone() != null ? ZoneId.of(branch.getTimezone()) : ZoneOffset.UTC;
    }
}
