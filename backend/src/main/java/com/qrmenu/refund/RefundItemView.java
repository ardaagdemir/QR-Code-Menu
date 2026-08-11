package com.qrmenu.refund;

import java.util.UUID;

public record RefundItemView(UUID orderItemId, int refundedQuantity, long refundAmountMinorUnits) {
}
