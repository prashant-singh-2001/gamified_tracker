package com.tracker.gateway.dto;

import com.tracker.gateway.user.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// #74: role removed. Public self-registration always yields USER (see AuthService.register);
// admins are provisioned out of band (app.admin.bootstrap.*), never via a client-supplied field.
// OLD:
// public record RegisterRequest(
//         @NotBlank(message = "firstName is required")
//         String firstName,
//         @NotBlank(message = "lastName is required")
//         String lastName,
//         @Email(message = "email should be formatted and required")
//         String email,
//         @NotBlank(message = "password is required")
//         String password,
//         @NotNull(message = "Role is required")
//         Role role
// ) {
// }
public record RegisterRequest(
        @NotBlank(message = "firstName is required")
        String firstName,
        @NotBlank(message = "lastName is required")
        String lastName,
        @Email(message = "email should be formatted and required")
        String email,
        @NotBlank(message = "password is required")
        String password
) {

    // Trimmed and lower-cased on the way in so "Ada@Example.com " and "ada@example.com" are one
    // account. Runs before Bean Validation, so stray whitespace is tolerated, not rejected.
    public RegisterRequest {
        email = Emails.normalize(email);
    }
}
