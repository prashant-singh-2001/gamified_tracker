package com.tracker.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Refresh Token Is Required")
        String refreshToken
) {
}
