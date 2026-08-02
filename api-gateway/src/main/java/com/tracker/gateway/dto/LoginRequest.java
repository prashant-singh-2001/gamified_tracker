package com.tracker.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        // @Email alone passes on both null and "", so @NotBlank carries the "required" half.
        @NotBlank(message = "email is required")
        @Email(message = "email should be formatted and required")
        String email,
        @NotBlank(message = "password is required")
        String password) {
}
