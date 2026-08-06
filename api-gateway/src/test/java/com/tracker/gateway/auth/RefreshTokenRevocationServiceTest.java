package com.tracker.gateway.auth;

import com.tracker.gateway.repository.RefreshTokenRepository;
import com.tracker.gateway.user.RefreshToken;
import com.tracker.gateway.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenRevocationServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenRevocationService refreshTokenRevocationService;

    /**
     * Verifies that an active refresh token is revoked
     * and persisted to the database.
     */
    @Test
    void shouldRevokeToken() {
        RefreshToken token = RefreshToken.builder()
                .token("token")
                .expiresAt(Instant.now().plusSeconds(60))
                .isRevoked(false)
                .isUsed(true)
                .build();

        when(refreshTokenRepository.save(token)).thenReturn(token);

        refreshTokenRevocationService.revoke(token);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    /**
     * Verifies that an already revoked refresh token
     * is not saved again.
     */
    @Test
    void shouldNotSaveAlreadyRevokedToken() {
        RefreshToken token = RefreshToken.builder()
                .token("token")
                .expiresAt(Instant.now().plusSeconds(60))
                .isUsed(true)
                .isRevoked(true)
                .build();

        refreshTokenRevocationService.revoke(token);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository, never()).save(any());
    }

    /**
     * Verifies that all active refresh tokens belonging
     * to a user are revoked and saved in a single operation.
     */
    @Test
    void shouldRevokeAllTokensForUser() {
        User user = new User();
        user.setId(1L);

        RefreshToken token1 = RefreshToken.builder()
                .token("token1")
                .expiresAt(Instant.now().plusSeconds(60))
                .isUsed(true)
                .isRevoked(false)
                .build();

        RefreshToken token2 = RefreshToken.builder()
                .token("token2")
                .expiresAt(Instant.now().plusSeconds(60))
                .isUsed(false)
                .isRevoked(false)
                .build();

        List<RefreshToken> tokens = List.of(token1, token2);

        when(refreshTokenRepository.findAllByUser_Id(user.getId())).thenReturn(tokens);

        refreshTokenRevocationService.revokeAllForUser(user.getId());

        assertThat(token1.isRevoked()).isTrue();
        assertThat(token2.isRevoked()).isTrue();

        verify(refreshTokenRepository).findAllByUser_Id(user.getId());
        verify(refreshTokenRepository).saveAll(tokens);
    }

    /**
     * Verifies that revoking tokens for a user with no
     * refresh tokens completes successfully and persists
     * an empty collection.
     */
    @Test
    void shouldHandleEmptyTokenList() {
        User user = new User();
        user.setId(1L);

        when(refreshTokenRepository.findAllByUser_Id(user.getId()))
                .thenReturn(List.of());

        refreshTokenRevocationService.revokeAllForUser(user.getId());

        verify(refreshTokenRepository).findAllByUser_Id(user.getId());
        verify(refreshTokenRepository).saveAll(List.of());
    }

    /**
     * Verifies that already revoked tokens remain unchanged,
     * while active tokens are revoked before persisting.
     */
    @Test
    void shouldNotModifyAlreadyRevokedTokens() {
        User user = new User();
        user.setId(1L);

        RefreshToken token1 = RefreshToken.builder()
                .token("token1")
                .isUsed(true)
                .isRevoked(true)
                .build();

        RefreshToken token2 = RefreshToken.builder()
                .token("token2")
                .isUsed(true)
                .isRevoked(false)
                .build();

        List<RefreshToken> tokens = List.of(token1, token2);

        when(refreshTokenRepository.findAllByUser_Id(user.getId()))
                .thenReturn(tokens);

        refreshTokenRevocationService.revokeAllForUser(user.getId());

        assertThat(token1.isRevoked()).isTrue();
        assertThat(token2.isRevoked()).isTrue();

        verify(refreshTokenRepository).findAllByUser_Id(user.getId());
        verify(refreshTokenRepository).saveAll(tokens);
    }
}