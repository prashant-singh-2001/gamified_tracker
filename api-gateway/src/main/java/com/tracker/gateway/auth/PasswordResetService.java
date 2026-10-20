package com.tracker.gateway.auth;

import com.tracker.gateway.config.PasswordResetProperties;
import com.tracker.gateway.exception.InvalidResetTokenException;
import com.tracker.gateway.repository.PasswordResetTokenRepository;
import com.tracker.gateway.repository.UserRepository;
import com.tracker.gateway.user.PasswordResetToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetNotifier notifier;
    private final PasswordResetProperties props;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRevocationService refreshTokenRevocationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordResetNotifier notifier,
                                PasswordResetProperties props,
                                PasswordEncoder passwordEncoder,
                                RefreshTokenRevocationService refreshTokenRevocationService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.notifier = notifier;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRevocationService = refreshTokenRevocationService;
    }

    /**
     * Issues a reset token for the account, if there is one. Returns nothing and never throws for an
     * unknown address: the caller answers identically either way, so the endpoint cannot be used to
     * find out which emails are registered. {@code email} is already normalised by the request DTO.
     */
    @Transactional
    public void requestReset(String email) {
        var user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }

        Instant now = Instant.now();
        // Only the newest token works; asking again retires the earlier ones.
        tokenRepository.invalidateUnusedForUser(user.getId(), now);

        String token = newToken();
        Instant expiresAt = now.plus(props.tokenTtl());
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash(hash(token))
                .user(user)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        try {
            notifier.sendResetToken(user.getEmail(), user.getFirstName(), token, expiresAt);
        } catch (RuntimeException e) {
            // A delivery failure must not change the response, or it would reveal the account exists.
            log.error("Could not deliver password reset for user {}", user.getId(), e);
        }
    }

    /**
     * Redeems a token and sets the new password. The token is consumed by a single conditional
     * UPDATE, so it works exactly once even under concurrent requests. Existing sessions are cut:
     * every refresh token of the user is revoked (an access token stays valid until it expires).
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String tokenHash = hash(token);
        var stored = tokenRepository.findByTokenHash(tokenHash).orElseThrow(InvalidResetTokenException::new);

        Instant now = Instant.now();
        if (tokenRepository.markUsedIfValid(tokenHash, now) == 0) {
            throw new InvalidResetTokenException();
        }

        var user = stored.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.invalidateUnusedForUser(user.getId(), now);
        refreshTokenRevocationService.revokeAllForUser(user.getId());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
