package com.tracker.gateway.security;

import com.tracker.gateway.auth.JwtUtil;
import com.tracker.gateway.user.Role;
import jakarta.servlet.ServletException;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #74: SecurityConfigTest mocks HttpSecurity and never invokes filterChain(), so the
// hasRole("ADMIN") matchers — including the pre-existing POST /api/activity precedent —
// have zero coverage today. This boots the real filter chain and mints real tokens with
// JwtUtil to exercise it end to end.
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    // Same hermetic setup as ApiGatewayApplicationTests — RateLimitConfig's real beans open
    // a live Redis connection at startup, which doesn't resolve outside docker-compose.
    @MockBean
    private RedisClient redisClient;

    @MockBean
    private StatefulRedisConnection<String, byte[]> redisConnection;

    @MockBean
    private AsyncProxyManager<String> asyncProxyManager;

    @MockBean
    private ProxyManager<String> proxyManager;

    @Test
    void postLevel_withUserToken_isForbidden() throws Exception {
        String token = jwtUtil.generateToken("user@example.com", Role.USER, 1L);

        mockMvc.perform(post("/api/level")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"xp\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postLevel_withAdminToken_isNotForbidden() throws Exception {
        String token = jwtUtil.generateToken("admin@example.com", Role.ADMIN, 99L);

        // Authorization passes for ADMIN, then the request reaches the gateway route's
        // load-balancer filter, which throws because no gamification-service instance is
        // registered in this test context (confirmed via the surefire log: "Granted
        // Authorities=[ROLE_ADMIN]" / "Secured POST /api/level" both precede the 503).
        // That downstream failure is out of scope here — only a clean 403 response, which
        // Spring Security always returns rather than throwing, would mean the ADMIN gate
        // itself is broken.
        try {
            MvcResult result = mockMvc.perform(post("/api/level")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activityId\":1,\"xp\":10}"))
                    .andReturn();
            assertNotEquals(403, result.getResponse().getStatus());
        } catch (ServletException expectedDownstreamRoutingFailure) {
            // authorization already let the request through by the time this was thrown
        }
    }

    @Test
    void postActivity_withUserToken_isForbidden() throws Exception {
        // Pre-existing precedent (SecurityConfig.java:47) — covered for the first time here too.
        String token = jwtUtil.generateToken("user@example.com", Role.USER, 1L);

        mockMvc.perform(post("/api/activity")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
