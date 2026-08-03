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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() throws Exception {
        setPrivateField(refreshTokenService, "refreshExpiration", 10000L);
    }

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

    @Test
    void shouldValidateRefreshTokenWhenValid() {
        RefreshToken token = RefreshToken.builder()
                .token("valid-token")
                .user(new User())
                .expiresAt(Instant.now().plusSeconds(60))
                .used(false)
                .build();

        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        RefreshToken result = refreshTokenService.validateRefreshToken("valid-token");

        assertThat(result).isSameAs(token);
    }

    @Test
    void shouldThrowWhenRefreshTokenNotFound() {
        when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("missing-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token not found");
    }

    @Test
    void shouldRevokeAndThrowWhenRefreshTokenExpired() {
        RefreshToken token = RefreshToken.builder()
                .token("expired-token")
                .user(new User())
                .expiresAt(Instant.now().minusSeconds(1))
                .used(false)
                .build();

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));
        doNothing().when(refreshTokenRepository).deleteByToken("expired-token");

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("expired-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token expired, please log in again");

        verify(refreshTokenRepository).deleteByToken("expired-token");
    }

    @Test
    void shouldRevokeAllAndThrowWhenRefreshTokenAlreadyUsed() {
        User user = new User();
        user.setId(2L);

        RefreshToken token = RefreshToken.builder()
                .token("used-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(100))
                .used(true)
                .build();

        when(refreshTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));
        doNothing().when(refreshTokenRepository).deleteByUser_Id(2L);

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("used-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Refresh token already used.");

        verify(refreshTokenRepository).deleteByUser_Id(2L);
    }

    @Test
    void shouldMarkRefreshTokenUsedAndSaveIt() {
        RefreshToken token = RefreshToken.builder()
                .token("mark-used-token")
                .user(new User())
                .expiresAt(Instant.now().plusSeconds(100))
                .used(false)
                .build();

        when(refreshTokenRepository.save(token)).thenReturn(token);

        refreshTokenService.markUsed(token);

        assertThat(token.isUsed()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void shouldRevokeTokenByTokenString() {
        doNothing().when(refreshTokenRepository).deleteByToken("revoke-token");

        refreshTokenService.revoke("revoke-token");

        verify(refreshTokenRepository).deleteByToken("revoke-token");
    }

    @Test
    void shouldRevokeAllTokensForUserId() {
        doNothing().when(refreshTokenRepository).deleteByUser_Id(5L);

        refreshTokenService.revokeAllForUser(5L);

        verify(refreshTokenRepository).deleteByUser_Id(5L);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
