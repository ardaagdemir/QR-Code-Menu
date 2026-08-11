package com.qrmenu.refund.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateRefundRequest(@NotEmpty List<@Valid RefundLineItemRequest> items) {
}
