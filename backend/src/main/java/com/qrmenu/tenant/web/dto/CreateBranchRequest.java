package com.qrmenu.tenant.web.dto;

import com.qrmenu.tenant.DeliveryModel;
import jakarta.validation.constraints.NotBlank;

public record CreateBranchRequest(@NotBlank String name, Boolean orderingEnabled, String address, DeliveryModel deliveryModel) {

    public boolean orderingEnabledOrDefault() {
        return orderingEnabled == null || orderingEnabled;
    }

    public DeliveryModel deliveryModelOrDefault() {
        return deliveryModel == null ? DeliveryModel.WAITER_DELIVERY : deliveryModel;
    }
}
