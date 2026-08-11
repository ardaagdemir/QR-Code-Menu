package com.qrmenu.refund;

import java.time.Instant;
import java.util.List;

/**
 * Section 4, customer-web screen #10: "Makbuzu Görüntüle / İndir ... yazdırılabilir
 * HTML, yasal fatura değildir" - deliberately not a legal invoice (no tax breakdown
 * table, no sequential fiscal numbering): just enough for the customer to have a
 * printable record of what they paid and, if applicable, what was refunded.
 */
public record ReceiptView(
        String businessName,
        String branchName,
        Integer orderNumber,
        Instant orderCreatedAt,
        List<ReceiptItemView> items,
        long totalMinorUnits,
        long totalRefundedMinorUnits,
        long netPaidMinorUnits,
        List<RefundView> refunds) {
}
