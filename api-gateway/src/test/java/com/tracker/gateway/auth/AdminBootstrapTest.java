package com.tracker.gateway.auth;

import com.tracker.gateway.config.AdminBootstrapProperties;
import com.tracker.gateway.repository.UserRepository;
import com.tracker.gateway.user.Role;
import com.tracker.gateway.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Test
    void shouldCreateAdminUser_whenNoneExistsForConfiguredEmail() {
        AdminBootstrapProperties properties =
                new AdminBootstrapProperties(true, "admin@example.com", "secret");
        AdminBootstrap bootstrap = new AdminBootstrap(properties, userRepository, passwordEncoder);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");

        bootstrap.run(null);

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("admin@example.com", saved.getEmail());
        assertEquals("encoded-secret", saved.getPassword());
        assertEquals(Role.ADMIN, saved.getRole());
    }

    @Test
    void shouldPromoteExistingUserToAdmin_whenEmailAlreadyRegisteredAsUser() {
        AdminBootstrapProperties properties =
                new AdminBootstrapProperties(true, "existing@example.com", "secret");
        AdminBootstrap bootstrap = new AdminBootstrap(properties, userRepository, passwordEncoder);

        User existing = new User();
        existing.setEmail("existing@example.com");
        existing.setRole(Role.USER);
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));

        bootstrap.run(null);

        verify(userRepository).save(userCaptor.capture());
        assertEquals(Role.ADMIN, userCaptor.getValue().getRole());
        // promotion path never touches the password
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldNoOp_whenExistingUserIsAlreadyAdmin() {
        AdminBootstrapProperties properties =
                new AdminBootstrapProperties(true, "admin@example.com", "secret");
        AdminBootstrap bootstrap = new AdminBootstrap(properties, userRepository, passwordEncoder);

        User existing = new User();
        existing.setEmail("admin@example.com");
        existing.setRole(Role.ADMIN);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrow_whenEnabledButEmailBlank() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties(true, "", "secret");
        AdminBootstrap bootstrap = new AdminBootstrap(properties, userRepository, passwordEncoder);

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void shouldThrow_whenEnabledButPasswordBlank() {
        AdminBootstrapProperties properties =
                new AdminBootstrapProperties(true, "admin@example.com", " ");
        AdminBootstrap bootstrap = new AdminBootstrap(properties, userRepository, passwordEncoder);

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verify(userRepository, never()).findByEmail(any());
    }
}
