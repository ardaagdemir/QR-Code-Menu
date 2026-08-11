package com.qrmenu.refund.web.dto;

import java.util.UUID;

public record RefundItemResponse(UUID orderItemId, int refundedQuantity, long refundAmountMinorUnits) {
}
