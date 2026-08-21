package com.qrmenu.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * A persisted row is a real expense. Manually-created rows remain editable; recurring
 * rows are immutable period snapshots so later template changes cannot rewrite history.
 * sourceTemplateId/generatedForPeriod are set only for scheduler-generated rows.
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

    /** Null for scheduler-generated recurring expenses because no staff actor initiated them. */
    @Column(name = "created_by_staff_user_id")
    private UUID createdByStaffUserId;

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
        this.sourceTemplateId = sourceTemplateId;
        this.generatedForPeriod = generatedForPeriod == null ? null : generatedForPeriod.toString();
        this.createdAt = Instant.now();
    }

    void applyManualEdit(
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

    public boolean isRecurring() {
        return sourceTemplateId != null;
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
