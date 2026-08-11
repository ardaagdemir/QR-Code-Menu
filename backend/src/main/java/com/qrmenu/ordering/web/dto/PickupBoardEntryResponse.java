package com.qrmenu.ordering.web.dto;

import java.util.UUID;

public record PickupBoardEntryResponse(UUID orderId, Integer orderNumber) {
}
