package com.qrmenu.tenant.web.dto;

import com.qrmenu.tenant.TableLocation;
import jakarta.validation.constraints.NotBlank;

/** location/capacity are optional - the internal API and older callers omit them and get INDOOR/no capacity (see TenantService.createTable). */
public record CreateTableRequest(@NotBlank String label, TableLocation location, Integer capacity) {
}
