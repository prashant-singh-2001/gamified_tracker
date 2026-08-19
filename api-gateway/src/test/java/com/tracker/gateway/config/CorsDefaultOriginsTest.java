package com.tracker.gateway.config;

import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

// #61: no CORS config existed anywhere in the repo. With CORS_ALLOWED_ORIGINS unset (the
// out-of-the-box default), every cross-origin request must be denied. Same hermetic
// Redis/Bucket4j mocking as SecurityRulesTest/ApiGatewayApplicationTests — RateLimitConfig's
// real beans open a live Redis connection at startup, which doesn't resolve outside docker-compose.
@SpringBootTest
@AutoConfigureMockMvc
class CorsDefaultOriginsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RedisClient redisClient;

    @MockBean
    private StatefulRedisConnection<String, byte[]> redisConnection;

    @MockBean
    private AsyncProxyManager<String> asyncProxyManager;

    @MockBean
    private ProxyManager<String> proxyManager;

    @Test
    void preflightFromAnyOrigin_getsNoAccessControlAllowOriginHeader() throws Exception {
        mockMvc.perform(options("/api/activity/Study")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
