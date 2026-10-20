package com.tracker.gateway.auth;

import com.tracker.gateway.dto.AuthResponse;
import com.tracker.gateway.dto.ForgotPasswordRequest;
import com.tracker.gateway.dto.ResetPasswordRequest;
import com.tracker.gateway.dto.LoginRequest;
import com.tracker.gateway.dto.RefreshRequest;
import com.tracker.gateway.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    // Always 202 with the same body, whether or not the address has an account (no user enumeration).
    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public java.util.Map<String, String> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.requestReset(req.email());
        return java.util.Map.of("message", "If an account exists for that email, a reset link has been sent.");
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.token(), req.newPassword());
    }
}
