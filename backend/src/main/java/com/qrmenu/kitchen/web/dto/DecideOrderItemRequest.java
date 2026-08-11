package com.qrmenu.kitchen.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DecideOrderItemRequest(@NotNull @Min(0) Integer acceptedQuantity) {
}
