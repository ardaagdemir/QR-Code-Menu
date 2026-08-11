package com.qrmenu.tenant.web.dto;

/** timezone may be null/blank - clears the override, falling back to Business.defaultTimeZone. */
public record SetBranchTimezoneRequest(String timezone) {
}
