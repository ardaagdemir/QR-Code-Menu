package com.qrmenu.tenant.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record BranchBusinessHoursEntryRequest(@NotNull DayOfWeek dayOfWeek, LocalTime openingTime, LocalTime closingTime, boolean closed) {
}
