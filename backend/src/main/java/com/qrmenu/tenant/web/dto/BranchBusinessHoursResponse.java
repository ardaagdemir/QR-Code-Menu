package com.qrmenu.tenant.web.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record BranchBusinessHoursResponse(DayOfWeek dayOfWeek, LocalTime openingTime, LocalTime closingTime, boolean closed) {
}
