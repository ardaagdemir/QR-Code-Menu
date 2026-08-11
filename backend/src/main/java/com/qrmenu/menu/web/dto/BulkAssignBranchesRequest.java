package com.qrmenu.menu.web.dto;

import com.qrmenu.menu.BranchAssignmentTarget;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BulkAssignBranchesRequest(@NotNull BranchAssignmentTarget target, List<UUID> branchIds) {

    public List<UUID> branchIdsOrEmpty() {
        return branchIds == null ? List.of() : branchIds;
    }
}
