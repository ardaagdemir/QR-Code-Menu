package com.qrmenu.expense;

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
import java.time.YearMonth;
import java.util.UUID;

/**
 * Section 16.1: DRAFT/SUBMITTED are freely editable by the creator; once APPROVED or
 * REJECTED the row is immutable (enforced in {@link ExpenseService}, mirrors
 * DailyBranchCloseReport's FINAL-lock pattern from gap-analysis #9). branchId is nullable
 * for business-level expenses (Section 16.1: "Business-level gider olabilir").
 * sourceTemplateId/generatedForPeriod are set only for rows the recurring scheduler drafted.
 */
@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Column(name = "incurred_at", nullable = false)
    private LocalDate incurredAt;

    @Column
    private String vendor;

    @Column
    private String description;

    @Column(name = "receipt_image_url")
    private String receiptImageUrl;

    /** Null for scheduler-generated recurring drafts (Section 16.2) - no staff actor initiated those. */
    @Column(name = "created_by_staff_user_id")
    private UUID createdByStaffUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExpenseStatus status;

    @Column(name = "approved_by_staff_user_id")
    private UUID approvedByStaffUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    @Column(name = "generated_for_period", length = 7)
    private String generatedForPeriod;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Expense() {
        // JPA
    }

    Expense(
            UUID businessId,
            UUID branchId,
            UUID categoryId,
            long amountMinorUnits,
            LocalDate incurredAt,
            String vendor,
            String description,
            String receiptImageUrl,
            UUID createdByStaffUserId,
            UUID sourceTemplateId,
            YearMonth generatedForPeriod) {
        this.businessId = businessId;
        this.branchId = branchId;
        this.categoryId = categoryId;
        this.amountMinorUnits = amountMinorUnits;
        this.incurredAt = incurredAt;
        this.vendor = vendor;
        this.description = description;
        this.receiptImageUrl = receiptImageUrl;
        this.createdByStaffUserId = createdByStaffUserId;
        this.status = ExpenseStatus.DRAFT;
        this.sourceTemplateId = sourceTemplateId;
        this.generatedForPeriod = generatedForPeriod == null ? null : generatedForPeriod.toString();
        this.createdAt = Instant.now();
    }

    void applyDraftEdit(
            UUID categoryId,
            long amountMinorUnits,
            LocalDate incurredAt,
            String vendor,
            String description,
            String receiptImageUrl) {
        this.categoryId = categoryId;
        this.amountMinorUnits = amountMinorUnits;
        this.incurredAt = incurredAt;
        this.vendor = vendor;
        this.description = description;
        this.receiptImageUrl = receiptImageUrl;
    }

    void submit() {
        this.status = ExpenseStatus.SUBMITTED;
    }

    void approve(UUID approvedByStaffUserId, Instant approvedAt) {
        this.status = ExpenseStatus.APPROVED;
        this.approvedByStaffUserId = approvedByStaffUserId;
        this.approvedAt = approvedAt;
    }

    void reject(UUID approvedByStaffUserId, Instant approvedAt) {
        this.status = ExpenseStatus.REJECTED;
        this.approvedByStaffUserId = approvedByStaffUserId;
        this.approvedAt = approvedAt;
    }

    public boolean isEditable() {
        return status == ExpenseStatus.DRAFT;
    }

    public boolean isLocked() {
        return status == ExpenseStatus.APPROVED || status == ExpenseStatus.REJECTED;
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

    public UUID getCategoryId() {
        return categoryId;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }

    public LocalDate getIncurredAt() {
        return incurredAt;
    }

    public String getVendor() {
        return vendor;
    }

    public String getDescription() {
        return description;
    }

    public String getReceiptImageUrl() {
        return receiptImageUrl;
    }

    public UUID getCreatedByStaffUserId() {
        return createdByStaffUserId;
    }

    public ExpenseStatus getStatus() {
        return status;
    }

    public UUID getApprovedByStaffUserId() {
        return approvedByStaffUserId;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public UUID getSourceTemplateId() {
        return sourceTemplateId;
    }

    public String getGeneratedForPeriod() {
        return generatedForPeriod;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
