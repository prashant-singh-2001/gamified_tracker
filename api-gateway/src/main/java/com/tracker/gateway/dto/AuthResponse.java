package com.tracker.gateway.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {
}
