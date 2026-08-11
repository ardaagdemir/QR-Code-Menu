package com.qrmenu.reporting;

import java.util.UUID;

/** Section 13.1: "kategori bazında ciro". */
public record CategorySalesView(UUID categoryId, String categoryName, long revenueMinorUnits) {
}
