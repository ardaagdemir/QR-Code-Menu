package com.qrmenu.expense;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringExpenseDuePolicyTest {

    @Test
    void templateCreatedAfterItsDueDayIsDueForTheCurrentPeriod() {
        LocalDate createdOn = LocalDate.of(2026, 8, 18);
        RecurringExpenseTemplate template = new RecurringExpenseTemplate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                25000,
                null,
                null,
                10,
                createdOn,
                null);

        assertThat(RecurringExpenseDuePolicy.dueDate(template, YearMonth.from(createdOn), createdOn))
                .contains(LocalDate.of(2026, 8, 10));
    }

    @Test
    void futureDueDateIsNotEligible() {
        LocalDate createdOn = LocalDate.of(2026, 8, 5);
        RecurringExpenseTemplate template = new RecurringExpenseTemplate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                25000,
                null,
                null,
                10,
                createdOn,
                null);

        assertThat(RecurringExpenseDuePolicy.dueDate(template, YearMonth.from(createdOn), createdOn)).isEmpty();
    }
}
