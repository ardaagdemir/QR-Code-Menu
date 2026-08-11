package com.qrmenu.ordering.web.dto;

import java.util.UUID;

public record CartItemOptionResponse(UUID id, String name, long priceDeltaMinorUnits) {
}
