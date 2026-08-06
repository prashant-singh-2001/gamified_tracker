package com.tracker.gateway.auth;

import com.tracker.gateway.dto.AuthResponse;
import com.tracker.gateway.dto.LoginRequest;
import com.tracker.gateway.dto.RegisterRequest;
import com.tracker.gateway.exception.InvalidCredentialsException;
import com.tracker.gateway.repository.UserRepository;
import com.tracker.gateway.user.RefreshToken;
import com.tracker.gateway.user.Role;
import com.tracker.gateway.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    /**
     * Verifies that a new user is registered successfully
     * and both access and refresh tokens are returned.
     */
    @Test
    void shouldRegisterNewUserAndReturnTokens() {
        RegisterRequest request = new RegisterRequest(
                "John",
                "Doe",
                "john@example.com",
                "pass123",
                Role.ADMIN
        );

        when(passwordEncoder.encode(request.password())).thenReturn("encoded-pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtUtil.generateToken(request.email(), request.role(), 1L)).thenReturn("access-token");

        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .user(new User())
                .build();
        when(refreshTokenService.generateRefreshToken(any(User.class))).thenReturn(refreshToken);

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("john@example.com");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-pass");
        assertThat(savedUser.getRole()).isEqualTo(Role.ADMIN);
    }

    /**
     * Verifies that the default USER role is assigned
     * when no role is provided during registration.
     */
    @Test
    void shouldUseDefaultRoleWhenRegisterRequestRoleIsNull() {
        RegisterRequest request = new RegisterRequest(
                "Jane",
                "Doe",
                "jane@example.com",
                "secret",
                null
        );

        when(passwordEncoder.encode(request.password())).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn("access-token-default").when(jwtUtil).generateToken(eq(request.email()), eq(Role.USER), any());
        when(refreshTokenService.generateRefreshToken(any(User.class)))
                .thenReturn(RefreshToken.builder().token("refresh-token-default").user(new User()).build());

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token-default");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-default");

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.USER);
    }

    /**
     * Verifies that a user with valid credentials
     * receives new access and refresh tokens.
     */
    @Test
    void shouldLoginSuccessfullyWithValidCredentials() {
        LoginRequest request = new LoginRequest("john@example.com", "pass123");
        User user = new User();
        user.setId(2L);
        user.setEmail(request.email());
        user.setPassword("encoded-pass");
        user.setRole(Role.USER);

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId())).thenReturn("access-token-login");
        when(refreshTokenService.generateRefreshToken(user)).thenReturn(RefreshToken.builder().token("refresh-token-login").user(user).build());

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token-login");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-login");
    }

    /**
     * Verifies that login fails when the user cannot be found.
     */
    @Test
    void shouldThrowWhenLoginUserNotFound() {
        LoginRequest request = new LoginRequest("missing@example.com", "pass123");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    /**
     * Verifies that login fails when the supplied password does not match the stored password.
     */
    @Test
    void shouldThrowWhenLoginPasswordDoesNotMatch() {
        LoginRequest request = new LoginRequest("john@example.com", "wrong-pass");
        User user = new User();
        user.setPassword("encoded-pass");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    /**
     * Verifies that a valid refresh token is consumed,
     * a new refresh token is issued, and a new access token is generated.
     */
    @Test
    void shouldRefreshTokensSuccessfully() {
        User user = new User();
        user.setId(3L);
        user.setEmail("jane@example.com");
        user.setRole(Role.USER);

        RefreshToken oldToken = RefreshToken.builder()
                .token("old-refresh")
                .user(user)
                .build();

        when(refreshTokenService.validateRefreshToken("old-refresh")).thenReturn(oldToken);
        when(refreshTokenService.generateRefreshToken(user)).thenReturn(RefreshToken.builder().token("new-refresh").user(user).build());
        when(jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId())).thenReturn("new-access");

        AuthResponse response = authService.refresh("old-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");

        verify(refreshTokenService).validateRefreshToken("old-refresh");
        verify(refreshTokenService).generateRefreshToken(user);
    }
}
