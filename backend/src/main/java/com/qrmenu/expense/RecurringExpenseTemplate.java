package com.qrmenu.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Section 16.2: monthly recurring expense template - the scheduler reads active rows and drafts an Expense per due period. */
@Entity
@Table(name = "recurring_expense_template")
public class RecurringExpenseTemplate {

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

    @Column
    private String vendor;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecurrenceType recurrence;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = true;

    protected RecurringExpenseTemplate() {
        // JPA
    }

    RecurringExpenseTemplate(
            UUID businessId,
            UUID branchId,
            UUID categoryId,
            long amountMinorUnits,
            String vendor,
            String description,
            int dayOfMonth,
            LocalDate startDate,
            LocalDate endDate) {
        this.businessId = businessId;
        this.branchId = branchId;
        this.categoryId = categoryId;
        this.amountMinorUnits = amountMinorUnits;
        this.vendor = vendor;
        this.description = description;
        this.recurrence = RecurrenceType.MONTHLY;
        this.dayOfMonth = dayOfMonth;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    void deactivate() {
        this.active = false;
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

    public String getVendor() {
        return vendor;
    }

    public String getDescription() {
        return description;
    }

    public RecurrenceType getRecurrence() {
        return recurrence;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActive() {
        return active;
    }
}
