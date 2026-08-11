package com.qrmenu.menu.web.dto;

import java.util.UUID;

public record MenuOptionResponse(UUID id, String name, long priceDeltaMinorUnits) {
}
