package com.qrmenu.customersession.web.dto;

import java.time.Instant;
import java.util.UUID;

public record TableVisitResponse(
        UUID tableVisitId,
        UUID businessId,
        UUID branchId,
        UUID tableId,
        String businessName,
        String branchName,
        String tableLabel,
        Instant startedAt,
        Integer guestCount) {
}
