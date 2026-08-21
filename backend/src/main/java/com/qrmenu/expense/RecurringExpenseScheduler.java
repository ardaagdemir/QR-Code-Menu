package com.qrmenu.expense;

import com.qrmenu.expense.repository.RecurringExpenseTemplateRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Section 16.2: the system creates an immutable expense snapshot for every due period.
 * Runs a few times a day rather than once and catches up every due,
 * missing period through today. Each template is generated in an isolated transaction;
 * template-row locking plus the database's template/period unique index makes concurrent
 * and repeated runs idempotent. Uses UTC (templates have no branch-timezone requirement
 * in the spec - "dayOfMonth" is a business-level calendar concept, not a store-closing
 * instant).
 */
@Component
class RecurringExpenseScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseScheduler.class);

    private final RecurringExpenseTemplateRepository templateRepository;
    private final RecurringExpenseGenerator expenseGenerator;

    RecurringExpenseScheduler(
            RecurringExpenseTemplateRepository templateRepository,
            RecurringExpenseGenerator expenseGenerator) {
        this.templateRepository = templateRepository;
        this.expenseGenerator = expenseGenerator;
    }

    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT3M")
    void generateDueExpenses() {
        List<UUID> templateIds = templateRepository.findAllByActiveTrueAndDeletedFalse().stream()
                .map(RecurringExpenseTemplate::getId)
                .toList();
        generateDueExpensesForTemplates(templateIds, LocalDate.now(ZoneOffset.UTC));
    }

    void generateDueExpensesForTemplates(List<UUID> templateIds, LocalDate asOfDate) {
        for (UUID templateId : templateIds) {
            try {
                expenseGenerator.generateDueExpenses(templateId, asOfDate);
            } catch (Exception e) {
                log.error("Failed to generate recurring expenses for template {}; skipping to the next template", templateId, e);
            }
        }
    }
}
