package com.tracker.gateway.dto;

import com.tracker.gateway.user.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email should be formatted and required")
        String email) {

    public ForgotPasswordRequest {
        email = Emails.normalize(email);
    }
}
