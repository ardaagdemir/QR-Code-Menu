package com.qrmenu.staffaccess.web.dto;

import com.qrmenu.staffaccess.StaffRole;
import jakarta.validation.constraints.NotNull;

public record ChangeStaffUserRoleRequest(@NotNull StaffRole role) {
}
