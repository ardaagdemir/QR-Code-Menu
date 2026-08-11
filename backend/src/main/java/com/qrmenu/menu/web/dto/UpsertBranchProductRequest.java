package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.BranchProductAvailability;
import jakarta.validation.constraints.NotNull;

public record UpsertBranchProductRequest(@NotNull BranchProductAvailability availability, Long priceOverrideMinorUnits) {
}
