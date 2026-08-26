package com.qrmenu.tenant;

import com.qrmenu.customersession.CheckInResult;

/** TenantService.checkIn's result: the resolved table plus the created/continued TableVisit. */
public record QrCheckInOutcome(TableReference tableReference, CheckInResult checkInResult) {
}
