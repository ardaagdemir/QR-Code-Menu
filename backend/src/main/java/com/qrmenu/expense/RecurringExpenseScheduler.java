package com.qrmenu.expense;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Section 16.2: "Sistem her dönem ilgili gider taslağını üretir; admin onaylayabilir/
 * düzenleyebilir." Runs a few times a day rather than once - idempotency is enforced by
 * {@code ExpenseRepository.existsBySourceTemplateIdAndGeneratedForPeriod} (same
 * self-correcting design as {@link DailyCloseScheduler}), so a missed or repeated run
 * never double-drafts a period. Uses UTC (templates have no branch-timezone requirement
 * in the spec - "dayOfMonth" is a business-level calendar concept, not a store-closing
 * instant).
 */
@Component
class RecurringExpenseScheduler {

    private final ExpenseService expenseService;

    RecurringExpenseScheduler(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT3M")
    void generateDueDrafts() {
        expenseService.generateDueDraftsForPeriod(LocalDate.now(ZoneOffset.UTC));
    }
}
