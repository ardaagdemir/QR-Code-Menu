package com.qrmenu.reporting.web.dto;

public record HourlySalesResponse(int hourOfDay, int orderCount, long revenueMinorUnits) {
}
