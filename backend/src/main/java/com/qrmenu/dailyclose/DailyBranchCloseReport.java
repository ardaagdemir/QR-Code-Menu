package com.qrmenu.dailyclose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Gap-analysis #9 (product-requirements.md Section 14.1): one row per branch per business
 * date. PREVIEW rows are recomputed/overwritten freely up until FINAL is generated; once
 * FINAL, the row is never recomputed (immutability enforced in {@link DailyCloseService},
 * not here) - Excel export always regenerates from these rows rather than being its own
 * source of truth (Section 14.3). No guestCount field - same gap as gap-analysis #8's
 * BranchSalesReportView (Section 13.3, still no guest-count data source).
 */
@Entity
@Table(name = "daily_branch_close_report")
public class DailyBranchCloseReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "gross_sales_minor_units", nullable = false)
    private long grossSalesMinorUnits;

    @Column(name = "refund_total_minor_units", nullable = false)
    private long refundTotalMinorUnits;

    @Column(name = "net_sales_minor_units", nullable = false)
    private long netSalesMinorUnits;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "accepted_order_count", nullable = false)
    private int acceptedOrderCount;

    @Column(name = "rejected_order_count", nullable = false)
    private int rejectedOrderCount;

    @Column(name = "average_order_value_minor_units", nullable = false)
    private long averageOrderValueMinorUnits;

    @Column(name = "table_visit_count", nullable = false)
    private long tableVisitCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DailyCloseStatus status;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected DailyBranchCloseReport() {
        // JPA
    }

    DailyBranchCloseReport(
            UUID businessId,
            UUID branchId,
            LocalDate businessDate,
            Instant periodStart,
            Instant periodEnd,
            long grossSalesMinorUnits,
            long refundTotalMinorUnits,
            long netSalesMinorUnits,
            int orderCount,
            int acceptedOrderCount,
            int rejectedOrderCount,
            long averageOrderValueMinorUnits,
            long tableVisitCount,
            DailyCloseStatus status,
            Instant generatedAt) {
        this.businessId = businessId;
        this.branchId = branchId;
        this.businessDate = businessDate;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.grossSalesMinorUnits = grossSalesMinorUnits;
        this.refundTotalMinorUnits = refundTotalMinorUnits;
        this.netSalesMinorUnits = netSalesMinorUnits;
        this.orderCount = orderCount;
        this.acceptedOrderCount = acceptedOrderCount;
        this.rejectedOrderCount = rejectedOrderCount;
        this.averageOrderValueMinorUnits = averageOrderValueMinorUnits;
        this.tableVisitCount = tableVisitCount;
        this.status = status;
        this.generatedAt = generatedAt;
    }

    void applyPreview(
            Instant periodStart,
            Instant periodEnd,
            long grossSalesMinorUnits,
            long refundTotalMinorUnits,
            long netSalesMinorUnits,
            int orderCount,
            int acceptedOrderCount,
            int rejectedOrderCount,
            long averageOrderValueMinorUnits,
            long tableVisitCount,
            Instant generatedAt) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.grossSalesMinorUnits = grossSalesMinorUnits;
        this.refundTotalMinorUnits = refundTotalMinorUnits;
        this.netSalesMinorUnits = netSalesMinorUnits;
        this.orderCount = orderCount;
        this.acceptedOrderCount = acceptedOrderCount;
        this.rejectedOrderCount = rejectedOrderCount;
        this.averageOrderValueMinorUnits = averageOrderValueMinorUnits;
        this.tableVisitCount = tableVisitCount;
        this.generatedAt = generatedAt;
    }

    void markFinal(Instant generatedAt) {
        this.status = DailyCloseStatus.FINAL;
        this.generatedAt = generatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public long getGrossSalesMinorUnits() {
        return grossSalesMinorUnits;
    }

    public long getRefundTotalMinorUnits() {
        return refundTotalMinorUnits;
    }

    public long getNetSalesMinorUnits() {
        return netSalesMinorUnits;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public int getAcceptedOrderCount() {
        return acceptedOrderCount;
    }

    public int getRejectedOrderCount() {
        return rejectedOrderCount;
    }

    public long getAverageOrderValueMinorUnits() {
        return averageOrderValueMinorUnits;
    }

    public long getTableVisitCount() {
        return tableVisitCount;
    }

    public DailyCloseStatus getStatus() {
        return status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
