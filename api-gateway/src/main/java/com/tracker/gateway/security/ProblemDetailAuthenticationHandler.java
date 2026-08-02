package com.tracker.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Makes the two responses Spring Security writes itself — the 401 for a missing/expired/malformed
 * bearer token and the 403 for an authenticated caller without the required role — use the same
 * RFC 7807 {@code application/problem+json} shape every {@code @RestControllerAdvice} in this
 * project returns.
 *
 * <p>The defaults ({@code BearerTokenAuthenticationEntryPoint} /
 * {@code BearerTokenAccessDeniedHandler}) write an empty body and communicate the reason only via
 * the {@code WWW-Authenticate} header. That is valid OAuth 2.0, but it means a client parsing this
 * API needs a second error format for exactly two status codes. The header is still emitted below,
 * so spec-compliant clients keep working — this only fills in the body.
 */
@Component
public class ProblemDetailAuthenticationHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final URI UNAUTHORIZED_TYPE = URI.create("https://tools.ietf.org/html/rfc6750#section-3.1");

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Deliberately generic: the exception can distinguish "expired" from "bad signature" from
        // "malformed", but echoing that back tells an attacker which half of a forged token to fix.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication required or token is invalid");
        problem.setType(UNAUTHORIZED_TYPE);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, problem);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Insufficient privileges for this endpoint");
        problem.setInstance(URI.create(request.getRequestURI()));

        write(response, HttpStatus.FORBIDDEN, problem);
    }

    private void write(HttpServletResponse response, HttpStatus status, ProblemDetail problem)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
