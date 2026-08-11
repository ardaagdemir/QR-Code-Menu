package com.qrmenu.refund;

public record ReceiptItemView(String productName, int quantity, long unitPriceMinorUnits, long lineTotalMinorUnits) {
}
