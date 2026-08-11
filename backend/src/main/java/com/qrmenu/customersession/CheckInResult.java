package com.qrmenu.customersession;

import java.util.UUID;

public record CheckInResult(UUID sessionId, TableVisit visit) {
}
