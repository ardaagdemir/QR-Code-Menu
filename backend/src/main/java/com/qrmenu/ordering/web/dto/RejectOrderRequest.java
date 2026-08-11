package com.qrmenu.ordering.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Section 6: "Red nedeni tutulmalıdır (reasonCode + optional note)". */
public record RejectOrderRequest(@NotBlank String reasonCode, String note) {
}
