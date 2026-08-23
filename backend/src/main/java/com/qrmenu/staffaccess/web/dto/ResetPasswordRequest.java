package com.qrmenu.staffaccess.web.dto;

import com.qrmenu.staffaccess.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH) String newPassword, @NotBlank String confirmNewPassword) {
}
