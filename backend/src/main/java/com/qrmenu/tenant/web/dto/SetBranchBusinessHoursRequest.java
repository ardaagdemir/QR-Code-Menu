package com.qrmenu.tenant.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SetBranchBusinessHoursRequest(@NotNull @Valid List<BranchBusinessHoursEntryRequest> days) {
}
