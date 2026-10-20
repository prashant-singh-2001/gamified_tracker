package com.tracker.gateway.auth;

import com.tracker.gateway.dto.AuthResponse;
import com.tracker.gateway.dto.LoginRequest;
import com.tracker.gateway.dto.RegisterRequest;
import com.tracker.gateway.exception.EmailAlreadyRegisteredException;
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
import org.springframework.dao.DataIntegrityViolationException;
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
                "pass123"
        );

        when(passwordEncoder.encode(request.password())).thenReturn("encoded-pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtUtil.generateToken(request.email(), Role.USER, 1L)).thenReturn("access-token");

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
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
    }

    /**
     * Verifies that the default USER role is assigned
     * when no role is provided during registration.
     */
    @Test
    void shouldAlwaysAssignUserRoleRegardlessOfCaller() {
        // #74: RegisterRequest no longer carries a role field at all — this pins the fix
        // that public self-registration can never produce anything but a USER account.
        RegisterRequest request = new RegisterRequest(
                "Jane",
                "Doe",
                "jane@example.com",
                "secret"
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

    @Test
    void shouldRejectDuplicateEmailWith409ExceptionAndNeverSave() {
        RegisterRequest request = new RegisterRequest("John", "Doe", "john@example.com", "pass123");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder, jwtUtil, refreshTokenService);
    }

    @Test
    void shouldTreatEmailsCaseInsensitivelyAndIgnoreSurroundingWhitespace() {
        // The same address in a different case, with padding, must hit the SAME lookup key as
        // the stored one -- RegisterRequest normalises on the way in.
        RegisterRequest request = new RegisterRequest("John", "Doe", "  John@Example.COM ", "pass123");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class, () -> authService.register(request));
    }

    @Test
    void shouldStoreEmailNormalised() {
        RegisterRequest request = new RegisterRequest("John", "Doe", "  John@Example.COM ", "pass123");
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtUtil.generateToken(eq("john@example.com"), eq(Role.USER), eq(1L))).thenReturn("access");
        when(refreshTokenService.generateRefreshToken(any(User.class)))
                .thenReturn(RefreshToken.builder().token("refresh").user(new User()).build());

        authService.register(request);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void shouldMapLostRaceToConflictWhenEmailNowExists() {
        // Both registrations passed the pre-check; this one lost at the unique constraint.
        RegisterRequest request = new RegisterRequest("John", "Doe", "john@example.com", "pass123");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false, true);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_email"));

        assertThrows(EmailAlreadyRegisteredException.class, () -> authService.register(request));

        verifyNoInteractions(jwtUtil, refreshTokenService);
    }

    @Test
    void shouldNotMislabelAnUnrelatedIntegrityFailureAsDuplicateEmail() {
        // If the email is NOT taken, the violation is something else and must surface as itself
        // (a 500) rather than a misleading "Email already registered".
        RegisterRequest request = new RegisterRequest("John", "Doe", "john@example.com", "pass123");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false, false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("some other constraint"));

        assertThrows(DataIntegrityViolationException.class, () -> authService.register(request));
    }

    @Test
    void shouldLoginWithEmailInDifferentCaseAndPadding() {
        LoginRequest request = new LoginRequest("  JOHN@Example.com ", "pass123");
        User user = new User();
        user.setId(2L);
        user.setEmail("john@example.com");
        user.setPassword("encoded-pass");
        user.setRole(Role.USER);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "encoded-pass")).thenReturn(true);
        when(jwtUtil.generateToken("john@example.com", Role.USER, 2L)).thenReturn("access");
        when(refreshTokenService.generateRefreshToken(user))
                .thenReturn(RefreshToken.builder().token("refresh").user(user).build());

        assertThat(authService.login(request).accessToken()).isEqualTo("access");
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
