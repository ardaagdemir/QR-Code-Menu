package com.qrmenu.expense.web.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RecurringExpenseTemplateResponse(
        UUID id,
        UUID branchId,
        UUID categoryId,
        String categoryName,
        long amountMinorUnits,
        String vendor,
        String description,
        int dayOfMonth,
        LocalDate startDate,
        LocalDate endDate,
        boolean active) {
}
