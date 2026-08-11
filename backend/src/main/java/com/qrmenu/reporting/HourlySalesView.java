package com.qrmenu.reporting;

/** Section 13.1: "saatlik satış dağılımı" - hourOfDay (0-23) is in the branch's own timezone. */
public record HourlySalesView(int hourOfDay, int orderCount, long revenueMinorUnits) {
}
