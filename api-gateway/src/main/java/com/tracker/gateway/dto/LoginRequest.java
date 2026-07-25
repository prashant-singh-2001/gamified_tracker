package com.tracker.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email(message = "email should be formatted and required")
        String email,
        @NotBlank(message = "password is required")
        String password) {
}
