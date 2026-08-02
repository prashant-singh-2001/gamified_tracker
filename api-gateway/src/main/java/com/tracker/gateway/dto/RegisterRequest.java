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
        @NotBlank(message = "email is required")
        @Email(message = "email should be formatted and required")
        String email,
        @NotBlank(message = "password is required")
        String password,
        // Deliberately NOT @NotNull: AuthService.register defaults a null role to Role.USER, so
        // requiring it here would have rejected the ordinary "register me as a normal user" call
        // the moment @Valid started being enforced. Optional in the constraint, defaulted in the
        // service — one rule, stated once.
        Role role
) {
}
