package com.qrmenu.staffaccess.web.dto;

import com.qrmenu.staffaccess.PasswordPolicy;
import com.qrmenu.staffaccess.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateStaffUserRequest(
        @NotBlank String email,
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH) String password,
        @NotNull StaffRole role,
        List<UUID> branchIds) {

    public List<UUID> branchIdsOrEmpty() {
        return branchIds == null ? List.of() : branchIds;
    }
}
