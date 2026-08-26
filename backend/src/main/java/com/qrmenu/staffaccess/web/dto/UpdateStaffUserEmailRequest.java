package com.qrmenu.staffaccess.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStaffUserEmailRequest(@NotBlank String email) {
}
