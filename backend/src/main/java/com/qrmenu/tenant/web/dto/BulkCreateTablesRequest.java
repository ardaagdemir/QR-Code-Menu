package com.qrmenu.tenant.web.dto;

import com.qrmenu.tenant.TableLocation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** namePrefix/capacity opsiyonel - boşsa prefix "Masa" olur, capacity boşsa oluşan masalarda null kalır (see TenantService.bulkCreateTables). */
public record BulkCreateTablesRequest(
        @NotNull TableLocation location, @NotNull @Min(1) @Max(100) Integer count, String namePrefix, Integer capacity) {
}
