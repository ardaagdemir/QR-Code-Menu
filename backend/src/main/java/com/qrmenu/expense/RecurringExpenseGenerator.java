package com.qrmenu.expense;

import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.expense.repository.ExpenseRepository;
import com.qrmenu.expense.repository.RecurringExpenseTemplateRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Generates immutable expense snapshots for all due, missing periods of one template. */
@Component
class RecurringExpenseGenerator {

    private final RecurringExpenseTemplateRepository templateRepository;
    private final ExpenseRepository expenseRepository;

    RecurringExpenseGenerator(
            RecurringExpenseTemplateRepository templateRepository,
            ExpenseRepository expenseRepository) {
        this.templateRepository = templateRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int generateDueExpenses(UUID templateId, LocalDate asOfDate) {
        RecurringExpenseTemplate template = templateRepository
                .findByIdForUpdate(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring expense template not found: " + templateId));
        if (!template.isActive() || template.isDeleted() || asOfDate.isBefore(template.getStartDate())) {
            return 0;
        }

        YearMonth firstPeriod = YearMonth.from(template.getStartDate());
        YearMonth currentPeriod = YearMonth.from(asOfDate);
        YearMonth lastPeriod = template.getEndDate() == null
                ? currentPeriod
                : min(currentPeriod, YearMonth.from(template.getEndDate()));
        int generatedCount = 0;

        for (YearMonth period = firstPeriod; !period.isAfter(lastPeriod); period = period.plusMonths(1)) {
            var dueDate = RecurringExpenseDuePolicy.dueDate(template, period, asOfDate);
            if (dueDate.isEmpty()
                    || expenseRepository.existsBySourceTemplateIdAndGeneratedForPeriod(templateId, period.toString())) {
                continue;
            }
            expenseRepository.save(RecurringExpenseDuePolicy.newExpense(template, period, dueDate.get()));
            generatedCount++;
        }
        expenseRepository.flush();
        return generatedCount;
    }

    private static YearMonth min(YearMonth left, YearMonth right) {
        return left.isBefore(right) ? left : right;
    }
}
