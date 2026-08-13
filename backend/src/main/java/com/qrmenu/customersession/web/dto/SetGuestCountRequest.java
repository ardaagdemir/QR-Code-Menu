package com.qrmenu.customersession.web.dto;

import jakarta.validation.constraints.Min;

/** guestCount == null clears/skips it - @Min only validates when a value is actually present. */
public record SetGuestCountRequest(@Min(1) Integer guestCount) {
}
