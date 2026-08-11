package com.qrmenu.expense.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateExpenseRequest(
        @NotNull UUID categoryId,
        @Positive long amountMinorUnits,
        @NotNull LocalDate incurredAt,
        String vendor,
        String description,
        String receiptImageUrl) {
}
