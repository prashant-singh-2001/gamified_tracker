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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #61: with a frontend origin configured via CORS_ALLOWED_ORIGINS, exactly that origin (and no
// other) must be allowed. Same hermetic Redis/Bucket4j mocking as CorsDefaultOriginsTest.
@SpringBootTest(properties = "cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
class CorsAllowedOriginTest {

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
    void preflightFromAllowedOrigin_getsAccessControlAllowOriginHeader() throws Exception {
        mockMvc.perform(options("/api/activity/Study")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void preflightFromDisallowedOrigin_getsNoAccessControlAllowOriginHeader() throws Exception {
        mockMvc.perform(options("/api/activity/Study")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
