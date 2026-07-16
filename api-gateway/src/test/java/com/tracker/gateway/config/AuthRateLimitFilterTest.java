package com.tracker.gateway.config;

import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRateLimitFilterTest {

    @SuppressWarnings("unchecked")
    private final ProxyManager<String> proxyManager = mock(ProxyManager.class);
    @SuppressWarnings("unchecked")
    private final RemoteBucketBuilder<String> builder = mock(RemoteBucketBuilder.class);
    private final BucketProxy bucket = mock(BucketProxy.class);

    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        when(proxyManager.builder()).thenReturn(builder);
        when(builder.build(anyString(), any(Supplier.class))).thenReturn(bucket);

        RateLimitProperties props = new RateLimitProperties(
                new RateLimitProperties.Bucket(100, 60),
                new RateLimitProperties.Bucket(100, 60),
                new RateLimitProperties.Bucket(10, 60));
        filter = new AuthRateLimitFilter(proxyManager, props);
    }

    @Test
    void shouldNotFilterNonAuthPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activity");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void filtersAuthPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void withinLimit_forwardsToChain() throws Exception {
        when(bucket.tryConsume(1)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotEquals(429, response.getStatus());
        // MockFilterChain records the request once doFilter() has been invoked.
        assertTrue(chain.getRequest() != null);
    }

    @Test
    void overLimit_returns429AndShortCircuitsChain() throws Exception {
        when(bucket.tryConsume(1)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("5.6.7.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Too many requests"));
        assertNull(chain.getRequest());
    }
}
