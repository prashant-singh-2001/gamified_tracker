package com.tracker.gateway.auth;

import com.tracker.gateway.exception.InvalidCredentialsException;
import com.tracker.gateway.repository.RefreshTokenRepository;
import com.tracker.gateway.user.RefreshToken;
import com.tracker.gateway.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevocationService refreshTokenRevocationService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, RefreshTokenRevocationService refreshTokenRevocationService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenRevocationService = refreshTokenRevocationService;
    }

    public RefreshToken generateRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpiration))
                .isUsed(false)
                .isRevoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token not found"));

        if (refreshToken.isRevoked()) {
            throw new InvalidCredentialsException("Refresh token has been revoked.");
        }

        if (refreshToken.isExpired()) {
            refreshTokenRevocationService.revoke(refreshToken);
            throw new InvalidCredentialsException("Refresh token expired, please log in again");
        }

        int tokenUpdate = refreshTokenRepository.markUsedIfTokenNotYetUsed(token);

        if (tokenUpdate == 0) {
            refreshTokenRevocationService.revokeAllForUser(refreshToken.getUser().getId());
            throw new InvalidCredentialsException("Refresh token already used.");
        }

        refreshToken.markUsed();

        return refreshToken;
    }
}