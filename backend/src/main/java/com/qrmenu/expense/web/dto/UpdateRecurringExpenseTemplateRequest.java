package com.qrmenu.expense.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateRecurringExpenseTemplateRequest(
        @NotNull UUID categoryId,
        @Positive long amountMinorUnits,
        String vendor,
        String description,
        @Min(1) @Max(31) int dayOfMonth,
        @NotNull LocalDate startDate,
        LocalDate endDate) {
}
