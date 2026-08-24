package com.qrmenu.platformadmin.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateBranchInfoRequest(@NotBlank String name, String address) {
}
