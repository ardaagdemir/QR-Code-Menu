package com.qrmenu.platformadmin.web.dto;

import com.qrmenu.staffaccess.StaffRole;
import jakarta.validation.constraints.NotNull;

public record ChangeStaffUserRoleRequest(@NotNull StaffRole role) {
}
