package com.tracker.gateway.security;

import com.tracker.gateway.auth.JwtUtil;
import com.tracker.gateway.user.Role;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void postThreshold_withUserToken_isForbidden() throws Exception {
        // #81: threshold rows drive the leveling curve for every user.
        String token = jwtUtil.generateToken("user@example.com", Role.USER, 1L);

        mockMvc.perform(post("/api/threshold")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"level\":2,\"xpRequired\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postThreshold_withAdminToken_isNotForbidden() throws Exception {
        String token = jwtUtil.generateToken("admin@example.com", Role.ADMIN, 99L);

        // Same downstream-routing caveat as postLevel_withAdminToken_isNotForbidden — no
        // gamification-service instance is registered in this test context.
        try {
            MvcResult result = mockMvc.perform(post("/api/threshold")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activityId\":1,\"level\":2,\"xpRequired\":100}"))
                    .andReturn();
            assertNotEquals(403, result.getResponse().getStatus());
        } catch (ServletException expectedDownstreamRoutingFailure) {
            // authorization already let the request through by the time this was thrown
        }
    }

    // #81 regression guard: POST /threshold/activity is a READ (getActivityLevelThresholdById)
    // that happens to use POST to carry a request body — it must stay open to any authenticated
    // user, not be swept up by a broader "/api/threshold/**" matcher.
    @Test
    @DisplayName("POST /threshold/activity stays open to a plain USER — it's a read, not a write (#81)")
    void postThresholdActivity_withUserToken_isNotForbidden() throws Exception {
        String token = jwtUtil.generateToken("user@example.com", Role.USER, 1L);

        try {
            MvcResult result = mockMvc.perform(post("/api/threshold/activity")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activityId\":1,\"level\":2,\"xpRequired\":100}"))
                    .andReturn();
            assertNotEquals(403, result.getResponse().getStatus());
        } catch (ServletException expectedDownstreamRoutingFailure) {
            // authorization already let the request through by the time this was thrown
        }
    }

    @Test
    void postRanksRecompute_withUserToken_isForbidden() throws Exception {
        // #83: full rank recompute is an expensive maintenance operation, not a user action.
        String token = jwtUtil.generateToken("user@example.com", Role.USER, 1L);

        mockMvc.perform(post("/api/ranks/recompute")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void postRanksRecompute_withAdminToken_isNotForbidden() throws Exception {
        String token = jwtUtil.generateToken("admin@example.com", Role.ADMIN, 99L);

        try {
            MvcResult result = mockMvc.perform(post("/api/ranks/recompute")
                            .header("Authorization", "Bearer " + token))
                    .andReturn();
            assertNotEquals(403, result.getResponse().getStatus());
        } catch (ServletException expectedDownstreamRoutingFailure) {
            // authorization already let the request through by the time this was thrown
        }
    }

    // #95: before the fix, this chain's .anyRequest().authenticated() also governed the
    // container's internal ERROR dispatch to /error. On that dispatch the re-authenticating
    // filters are skipped, the context is anonymous, /error matched no permitAll entry, and
    // Spring Security wrote an empty 403 over the real downstream status — masking every
    // proxied error (a 404, a 500, anything) identically. Simulating the ERROR dispatch
    // directly (rather than forcing a real downstream failure) isolates the security-chain
    // behavior from routing/load-balancer noise.
    @Test
    @DisplayName("an internal ERROR dispatch is permitted, so a real downstream status survives instead of an empty 403 (#95)")
    void errorDispatch_isPermittedAndKeepsTheOriginalStatus() throws Exception {
        mockMvc.perform(get("/error")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
                            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/activitylog/");
                            return request;
                        }))
                .andExpect(status().isNotFound()); // was 403 with an empty body before the fix
    }
}
