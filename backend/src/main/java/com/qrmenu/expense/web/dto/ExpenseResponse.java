package com.qrmenu.expense.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID branchId,
        UUID categoryId,
        String categoryName,
        long amountMinorUnits,
        LocalDate incurredAt,
        String vendor,
        String description,
        String receiptImageUrl,
        String status,
        UUID approvedByStaffUserId,
        Instant approvedAt,
        UUID sourceTemplateId,
        Instant createdAt) {
}
