package com.qrmenu.tenant.web.dto;

import com.qrmenu.tenant.DeliveryModel;
import jakarta.validation.constraints.NotNull;

public record SetDeliveryModelRequest(@NotNull DeliveryModel deliveryModel) {
}
