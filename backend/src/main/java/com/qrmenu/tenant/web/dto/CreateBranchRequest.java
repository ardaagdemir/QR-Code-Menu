package com.qrmenu.tenant.web.dto;

import com.qrmenu.tenant.DeliveryModel;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalTime;

public record CreateBranchRequest(
        @NotBlank String name,
        Boolean orderingEnabled,
        LocalTime openingTime,
        LocalTime closingTime,
        DeliveryModel deliveryModel) {

    public boolean orderingEnabledOrDefault() {
        return orderingEnabled == null || orderingEnabled;
    }

    public DeliveryModel deliveryModelOrDefault() {
        return deliveryModel == null ? DeliveryModel.WAITER_DELIVERY : deliveryModel;
    }
}
