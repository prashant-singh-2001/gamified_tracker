package com.tracker.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ProblemDetail auth handler")
class ProblemDetailAuthenticationHandlerTest {

    private final ProblemDetailAuthenticationHandler handler =
            new ProblemDetailAuthenticationHandler(new ObjectMapper());

    @Test
    @DisplayName("401 is problem+json, keeps WWW-Authenticate, and leaks no token detail")
    void commence_writesProblemDetail() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/level/1");
        var response = new MockHttpServletResponse();

        handler.commence(request, response, new InvalidBearerTokenException("JWT expired at 2026-07-30"));

        assertEquals(401, response.getStatus());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));

        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":401"));
        assertTrue(body.contains("/api/level/1"), "instance should point at the attempted path");
        // The underlying reason must not be echoed back — it tells an attacker what to fix.
        assertFalse(body.contains("expired"), "must not disclose why the token was rejected");
    }

    @Test
    @DisplayName("403 is problem+json for an authenticated caller lacking the role")
    void handle_writesProblemDetail() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/activity");
        var response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Access Denied"));

        assertEquals(403, response.getStatus());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());

        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":403"));
        assertTrue(body.contains("Insufficient privileges"));
    }
}
