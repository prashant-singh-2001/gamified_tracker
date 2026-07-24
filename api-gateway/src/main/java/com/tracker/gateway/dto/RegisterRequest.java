package com.tracker.gateway.dto;

import com.tracker.gateway.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotBlank(message = "firstName is required")
        String firstName,
        @NotBlank(message = "lastName is required")
        String lastName,
        @Email(message = "email should be formatted and required")
        String email,
        @NotBlank(message = "password is required")
        String password,
        @NotNull(message = "Role is required")
        Role role
) {
}
