package com.tracker.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "token is required")
        String token,
        @NotBlank(message = "newPassword is required")
        String newPassword) {
}
