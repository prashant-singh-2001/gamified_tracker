package com.tracker.gateway.auth;

import com.tracker.gateway.exception.InvalidCredentialsException;
import com.tracker.gateway.repository.RefreshTokenRepository;
import com.tracker.gateway.user.RefreshToken;
import com.tracker.gateway.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public RefreshToken generateRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpiration))
                .used(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token not found"));

        if (refreshToken.isExpired()) {
            revoke(refreshToken.getToken());
            throw new InvalidCredentialsException("Refresh token expired, please log in again");
        }

        if (refreshToken.isUsed()) {
            revokeAllForUser(refreshToken.getUser().getId());
            throw new InvalidCredentialsException("Refresh token already used.");
        }

        return refreshToken;
    }

    @Transactional
    public void markUsed(RefreshToken token) {
        token.markUsed();
        refreshTokenRepository.save(token);
    }

    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.deleteByUser_Id(userId);
    }
}