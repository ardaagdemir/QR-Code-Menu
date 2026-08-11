package com.qrmenu.reporting;

import java.util.UUID;

/** Section 13.1: "ürün bazında satılan adet" / "ürün bazında ciro". */
public record ProductSalesView(UUID productId, String productName, int quantitySold, long revenueMinorUnits) {
}
