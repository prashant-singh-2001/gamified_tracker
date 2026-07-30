package com.tracker.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@Component
public class UserIdHeaderFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "userId";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityContextHolder.getContext().getAuthentication() == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }

        Object rawUserId = jwtAuth.getToken().getClaim("userId");
        if (rawUserId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        final String trustedUserId = String.valueOf(rawUserId);

        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return USER_ID_HEADER.equalsIgnoreCase(name) ? trustedUserId : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return USER_ID_HEADER.equalsIgnoreCase(name)
                        ? Collections.enumeration(List.of(trustedUserId))
                        : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                names.removeIf(n -> USER_ID_HEADER.equalsIgnoreCase(n));
                names.add(USER_ID_HEADER);
                return Collections.enumeration(names);
            }
        };

        chain.doFilter(wrapper, response);
    }
}