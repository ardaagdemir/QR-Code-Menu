package com.qrmenu.refund.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RefundLineItemRequest(@NotNull UUID orderItemId, @Min(1) int quantity) {
}
