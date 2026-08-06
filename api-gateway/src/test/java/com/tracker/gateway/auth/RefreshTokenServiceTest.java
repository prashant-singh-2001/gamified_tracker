package com.tracker.gateway.auth;

import com.tracker.gateway.exception.InvalidCredentialsException;
import com.tracker.gateway.repository.RefreshTokenRepository;
import com.tracker.gateway.user.RefreshToken;
import com.tracker.gateway.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenRevocationService refreshTokenRevocationService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() throws Exception {
        setPrivateField(refreshTokenService, "refreshExpiration", 10000L);
    }

    /**
     * Verifies that a new refresh token is generated with the
     * expected properties and persisted successfully.
     */
    @Test
    void shouldGenerateRefreshTokenAndSaveIt() {
        User user = new User();
        user.setId(1L);
        user.setEmail("john@example.com");

        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken token = refreshTokenService.generateRefreshToken(user);

        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.getToken()).isNotNull().isNotEmpty();
        assertThat(token.isExpired()).isFalse();
        assertThat(token.isUsed()).isFalse();
        verify(refreshTokenRepository).save(token);
    }

    /**
     * Verifies that a valid refresh token is returned
     * when it exists and passes all validation checks.
     */
    @Test
    void shouldValidateRefreshTokenWhenValid() {
        User user = new User();
        user.setId(1L);

        RefreshToken token = RefreshToken.builder()
                .token("valid-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .isUsed(false)
                .isRevoked(false)
                .build();

        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        // Simulate successful atomic update (1 row updated)
        when(refreshTokenRepository.markUsedIfTokenNotYetUsed("valid-token")).thenReturn(1);

        RefreshToken result = refreshTokenService.validateRefreshToken("valid-token");

        assertThat(result).isSameAs(token);
        // The entity's 'used' flag should be set to true (by the method)
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokenRepository).markUsedIfTokenNotYetUsed("valid-token");
        verify(refreshTokenRevocationService, never()).revokeAllForUser(anyLong());
    }

    /**
     * Verifies that an exception is thrown when the
     * requested refresh token cannot be found.
     */
    @Test
    void shouldThrowWhenRefreshTokenNotFound() {
        when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("missing-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token not found");
    }

    /**
     * Verifies that an expired refresh token is revoked
     * and an exception is thrown to prevent its reuse.
     */
    @Test
    void shouldRevokeAndThrowWhenRefreshTokenExpired() {
        RefreshToken token = RefreshToken.builder()
                .token("expired-token")
                .user(new User())
                .expiresAt(Instant.now().minusSeconds(1))
                .isUsed(false)
                .isRevoked(false)
                .build();

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));
        doNothing().when(refreshTokenRevocationService).revoke(token);

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("expired-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token expired, please log in again");

        verify(refreshTokenRepository).findByToken("expired-token");
        verify(refreshTokenRevocationService).revoke(token);
        // The update method should NOT be called because we throw before reaching it
        verify(refreshTokenRepository, never()).markUsedIfTokenNotYetUsed(anyString());
    }

    /**
     * Verifies that reuse of an already used refresh token
     * revokes all refresh tokens for the user and throws
     * an exception.
     */
    @Test
    void shouldRevokeAllAndThrowWhenRefreshTokenAlreadyUsed() {
        User user = new User();
        user.setId(2L);

        RefreshToken token = RefreshToken.builder()
                .token("used-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(100))
                .isUsed(false)  // the database still has isUsed=false, but the token was already consumed – we simulate concurrency
                .isRevoked(false)
                .build();

        when(refreshTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));
        // Repository update returns 0 because the token was already marked used by another request
        when(refreshTokenRepository.markUsedIfTokenNotYetUsed("used-token")).thenReturn(0);
        doNothing().when(refreshTokenRevocationService).revokeAllForUser(user.getId());

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("used-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token already used.");

        verify(refreshTokenRepository).findByToken("used-token");
        verify(refreshTokenRevocationService).revokeAllForUser(user.getId());
        verify(refreshTokenRepository).markUsedIfTokenNotYetUsed("used-token");
    }

    /**
     * Verifies that marking a refresh token as used updates
     * its state and persists the change.
     */
    @Test
    void shouldThrowWhenRefreshTokenIsRevoked() {
        RefreshToken token = RefreshToken.builder()
                .token("revoked-token")
                .user(new User())
                .expiresAt(Instant.now().plusSeconds(60))
                .isUsed(false)
                .isRevoked(true)
                .build();

        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("revoked-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token has been revoked.");

        verify(refreshTokenRepository, never()).markUsedIfTokenNotYetUsed(anyString());
        verify(refreshTokenRevocationService, never()).revoke(any());
    }

    /**
     * Sets the value of a private field using reflection.
     * Used to inject test configuration values.
     */
    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
