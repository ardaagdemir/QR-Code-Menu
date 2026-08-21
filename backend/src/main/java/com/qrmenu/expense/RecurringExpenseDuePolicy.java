package com.qrmenu.expense;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/** Calendar rules shared by immediate template creation and scheduled catch-up. */
final class RecurringExpenseDuePolicy {

    private RecurringExpenseDuePolicy() {
    }

    static Optional<LocalDate> dueDate(
            RecurringExpenseTemplate template,
            YearMonth period,
            LocalDate asOfDate) {
        if (!template.isActive() || template.isDeleted() || asOfDate.isBefore(template.getStartDate())) {
            return Optional.empty();
        }
        if (period.isBefore(YearMonth.from(template.getStartDate()))
                || period.isAfter(YearMonth.from(asOfDate))) {
            return Optional.empty();
        }

        LocalDate dueDate = period.atDay(Math.min(template.getDayOfMonth(), period.lengthOfMonth()));
        if (dueDate.isAfter(asOfDate)) {
            return Optional.empty();
        }
        if (template.getEndDate() != null && dueDate.isAfter(template.getEndDate())) {
            return Optional.empty();
        }
        return Optional.of(dueDate);
    }

    static Expense newExpense(RecurringExpenseTemplate template, YearMonth period, LocalDate dueDate) {
        return new Expense(
                template.getBusinessId(),
                template.getBranchId(),
                template.getCategoryId(),
                template.getAmountMinorUnits(),
                dueDate,
                template.getVendor(),
                template.getDescription(),
                null,
                null,
                template.getId(),
                period);
    }
}
