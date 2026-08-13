package com.qrmenu.tenant.web.dto;

import jakarta.validation.constraints.Positive;

/** Section 6/27: kasa kabul bekleme timeout'u saniye cinsinden - sıfır/negatif reddedilir. */
public record SetStoreAcceptanceTimeoutRequest(@Positive int timeoutSeconds) {
}
