package com.tracker.gateway.security;

import com.tracker.gateway.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    @Mock
    private UserIdHeaderFilter userIdHeaderFilter;

    @Mock
    private HttpSecurity httpSecurity;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private ProblemDetailAuthenticationHandler problemDetailAuthenticationHandler;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        securityConfig = new SecurityConfig(userIdHeaderFilter, problemDetailAuthenticationHandler);
    }

    @Test
    void passwordEncoder_shouldReturnBCryptPasswordEncoder() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        assertNotNull(encoder);
        assertTrue(encoder instanceof BCryptPasswordEncoder);

        String encoded = encoder.encode("password");
        assertTrue(encoder.matches("password", encoded));
    }

    @Test
    void jwtDecoder_shouldCreateDecoder() {
        String secret = Base64.getEncoder()
                .encodeToString("12345678901234567890123456789012".getBytes());

        JwtDecoder decoder = securityConfig.jwtDecoder(secret);

        assertNotNull(decoder);
    }

    @Test
    void jwtAuthenticationConverter_shouldMapAdminRole() {

        JwtAuthenticationConverter converter =
                securityConfig.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("role", "ADMIN")
                .build();

        var authentication = converter.convert(jwt);

        assertNotNull(authentication);
        assertEquals(
                List.of(new SimpleGrantedAuthority(Role.ADMIN.authority())),
                authentication.getAuthorities().stream().toList()
        );
    }

    @Test
    void jwtAuthenticationConverter_shouldDefaultToUserRole() {

        JwtAuthenticationConverter converter =
                securityConfig.jwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("abc", "user")
                .build();

        var authentication = converter.convert(jwt);

        assertNotNull(authentication);
        assertEquals(
                List.of(new SimpleGrantedAuthority(Role.USER.authority())),
                authentication.getAuthorities().stream().toList()
        );
    }
}