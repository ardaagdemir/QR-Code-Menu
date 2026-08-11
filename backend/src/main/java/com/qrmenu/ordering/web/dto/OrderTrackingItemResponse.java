package com.qrmenu.ordering.web.dto;

public record OrderTrackingItemResponse(String productName, int orderedQuantity, int acceptedQuantity, int rejectedQuantity, String status) {
}
