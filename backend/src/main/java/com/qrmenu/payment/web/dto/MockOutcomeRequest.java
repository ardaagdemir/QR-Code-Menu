package com.qrmenu.payment.web.dto;

import com.qrmenu.payment.WebhookOutcome;
import jakarta.validation.constraints.NotNull;

public record MockOutcomeRequest(@NotNull WebhookOutcome outcome) {
}
