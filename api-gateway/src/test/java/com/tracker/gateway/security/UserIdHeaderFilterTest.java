package com.tracker.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserIdHeaderFilterTest {

    private UserIdHeaderFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserIdHeaderFilter();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilter_whenAuthenticationMissing() {

        MockHttpServletRequest request = new MockHttpServletRequest();

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldFilter_whenAuthenticationPresent() {

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("userId", 100L)
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));

        MockHttpServletRequest request = new MockHttpServletRequest();

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void shouldReturn401_whenUserIdClaimMissing() throws Exception {

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("abc", "user")
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                response,
                mock(FilterChain.class));

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void shouldAddUserIdHeader() throws Exception {

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("userId", 12345L)
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> header = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            header.set(
                    ((jakarta.servlet.http.HttpServletRequest) req)
                            .getHeader("userId"));
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals("12345", header.get());
    }

    @Test
    void shouldContinueFilterChain_whenAuthenticationIsNotJwt() throws Exception {

        SecurityContextHolder.getContext().setAuthentication(
                mock(org.springframework.security.core.Authentication.class));

        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                chain);

        verify(chain).doFilter(any(), any());
    }
}