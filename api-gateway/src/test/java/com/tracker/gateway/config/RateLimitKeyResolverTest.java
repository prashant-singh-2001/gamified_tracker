package com.tracker.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitKeyResolverTest {

    private String resolve(MockHttpServletRequest servletRequest) {
        ServerRequest serverRequest = mock(ServerRequest.class);
        when(serverRequest.servletRequest()).thenReturn(servletRequest);
        return RateLimitKeyResolver.byUserIdOrIp().apply(serverRequest);
    }

    @Test
    void keysOnTrustedUserIdHeaderWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("userId", "42");

        assertEquals("user:42", resolve(request));
    }

    @Test
    void ignoresBlankUserIdHeaderAndFallsBackToIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("userId", "   ");
        request.addHeader("X-Forwarded-For", "9.9.9.9");

        assertEquals("ip:9.9.9.9", resolve(request));
    }

    @Test
    void fallsBackToFirstXForwardedForEntryWhenUserIdMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");

        assertEquals("ip:1.2.3.4", resolve(request));
    }

    @Test
    void fallsBackToRemoteAddrWhenNoUserIdAndNoXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");

        assertEquals("ip:10.0.0.5", resolve(request));
    }
}
