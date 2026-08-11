package com.qrmenu.ordering.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record AddCartItemRequest(@NotNull UUID productId, @Min(1) int quantity, List<UUID> selectedOptionIds) {

    public List<UUID> selectedOptionIdsOrEmpty() {
        return selectedOptionIds == null ? List.of() : selectedOptionIds;
    }
}
