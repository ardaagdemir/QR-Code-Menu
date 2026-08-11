package com.qrmenu.refund.web.dto;

public record ReceiptItemResponse(String productName, int quantity, long unitPriceMinorUnits, long lineTotalMinorUnits) {
}
