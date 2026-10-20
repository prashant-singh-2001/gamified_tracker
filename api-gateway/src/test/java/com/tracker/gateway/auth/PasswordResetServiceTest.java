package com.tracker.gateway.auth;

import com.tracker.gateway.config.PasswordResetProperties;
import com.tracker.gateway.exception.InvalidResetTokenException;
import com.tracker.gateway.repository.PasswordResetTokenRepository;
import com.tracker.gateway.repository.UserRepository;
import com.tracker.gateway.user.PasswordResetToken;
import com.tracker.gateway.user.Role;
import com.tracker.gateway.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Password reset service")
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private PasswordResetNotifier notifier;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRevocationService refreshTokenRevocationService;

    private PasswordResetService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, notifier,
                new PasswordResetProperties(Duration.ofMinutes(30), false), passwordEncoder,
                refreshTokenRevocationService);
        user = User.builder().id(7L).firstName("Ada").lastName("L").email("ada@example.com")
                .password("old-hash").role(Role.USER).build();
    }

    @Test
    @DisplayName("an unknown email does nothing and throws nothing (no enumeration)")
    void unknownEmailIsSilent() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestReset("nobody@example.com"));

        verifyNoInteractions(tokenRepository, notifier);
    }

    @Test
    @DisplayName("a known email stores only the HASH of the token and delivers the raw token")
    void issuesHashedToken() {
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        service.requestReset("ada@example.com");

        var stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(stored.capture());
        var raw = ArgumentCaptor.forClass(String.class);
        var expires = ArgumentCaptor.forClass(Instant.class);
        verify(notifier).sendResetToken(eq("ada@example.com"), eq("Ada"), raw.capture(), expires.capture());

        assertNotEquals(raw.getValue(), stored.getValue().getTokenHash());
        assertEquals(PasswordResetService.hash(raw.getValue()), stored.getValue().getTokenHash());
        assertEquals(64, stored.getValue().getTokenHash().length());
        assertTrue(raw.getValue().length() >= 43, "256 random bits, base64url");
        assertEquals(expires.getValue(), stored.getValue().getExpiresAt());
        assertTrue(expires.getValue().isAfter(Instant.now().plus(Duration.ofMinutes(29))));
        assertTrue(expires.getValue().isBefore(Instant.now().plus(Duration.ofMinutes(31))));
    }

    @Test
    @DisplayName("each request mints a different token and retires the user's earlier unused ones")
    void newRequestRetiresEarlierTokens() {
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        service.requestReset("ada@example.com");
        service.requestReset("ada@example.com");

        verify(tokenRepository, times(2)).invalidateUnusedForUser(eq(7L), any(Instant.class));
        var raw = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(2)).sendResetToken(anyString(), anyString(), raw.capture(), any());
        assertNotEquals(raw.getAllValues().get(0), raw.getAllValues().get(1));
    }

    @Test
    @DisplayName("a failing notifier does not change the outcome (it would leak that the account exists)")
    void notifierFailureIsSwallowed() {
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException("smtp down")).when(notifier).sendResetToken(any(), any(), any(), any());

        assertDoesNotThrow(() -> service.requestReset("ada@example.com"));
    }

    private PasswordResetToken storedFor(String raw) {
        return PasswordResetToken.builder().tokenHash(PasswordResetService.hash(raw)).user(user)
                .expiresAt(Instant.now().plusSeconds(600)).createdAt(Instant.now()).build();
    }

    @Test
    @DisplayName("a valid token sets the new password (hashed), retires other tokens and revokes sessions")
    void resetHappyPath() {
        String raw = "the-token";
        String hash = PasswordResetService.hash(raw);
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(storedFor(raw)));
        when(tokenRepository.markUsedIfValid(eq(hash), any(Instant.class))).thenReturn(1);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

        service.resetPassword(raw, "new-pass");

        assertEquals("new-hash", user.getPassword());
        verify(userRepository).save(user);
        verify(tokenRepository).invalidateUnusedForUser(eq(7L), any(Instant.class));
        verify(refreshTokenRevocationService).revokeAllForUser(7L);
    }

    @Test
    @DisplayName("an unknown token is rejected and nothing changes")
    void resetUnknownToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidResetTokenException.class, () -> service.resetPassword("nope", "new-pass"));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRevocationService);
    }

    @Test
    @DisplayName("an expired or already used token (the conditional update matches nothing) is rejected")
    void resetUsedOrExpiredToken() {
        String raw = "the-token";
        String hash = PasswordResetService.hash(raw);
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(storedFor(raw)));
        when(tokenRepository.markUsedIfValid(eq(hash), any(Instant.class))).thenReturn(0);

        var ex = assertThrows(InvalidResetTokenException.class, () -> service.resetPassword(raw, "new-pass"));

        assertEquals("Invalid or expired reset token", ex.getMessage());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRevocationService);
    }
}
