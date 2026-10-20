package com.tracker.gateway.dto;

import com.tracker.gateway.user.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email(message = "email should be formatted and required")
        String email,
        @NotBlank(message = "password is required")
        String password) {

    // Trimmed and lower-cased on the way in, so login compares emails the same way registration
    // stores them. Runs before Bean Validation, so stray whitespace is tolerated, not rejected.
    public LoginRequest {
        email = Emails.normalize(email);
    }
}
