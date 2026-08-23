package com.qrmenu.refund.web.dto;

import java.time.Instant;

/** Customer-facing refund line for the printable receipt - no internal refund/order/item ids. */
public record ReceiptRefundResponse(String status, long totalAmountMinorUnits, Instant createdAt) {
}
