package com.tracker.gateway.config;

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
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Pins that the published /v3/api-docs describes the /auth error bodies, including the 429 whose body
// is NOT a ProblemDetail. Same hermetic Redis mocks as SecurityRulesTest.
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiErrorExamplesTest {

    @MockBean
    private RedisClient redisClient;

    @MockBean
    private StatefulRedisConnection<String, byte[]> redisConnection;

    @MockBean
    private AsyncProxyManager<String> asyncProxyManager;

    @MockBean
    private ProxyManager<String> proxyManager;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("login documents 401 with the fixed message; register documents 400; both document 429")
    void authEndpointsDocumentTheirErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses", hasKey("401")))
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses", hasKey("429")))
                .andExpect(jsonPath("$.paths['/auth/register'].post.responses", hasKey("400")))
                .andExpect(jsonPath("$.paths['/auth/register'].post.responses", hasKey("429")))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses", hasKey("401")))
                .andExpect(jsonPath("$.components.examples.ProblemInvalidCredentials.value.detail",
                        is("Invalid email or password")));
    }

    @Test
    @DisplayName("the 429 body is documented as {error}, not as a Problem")
    void rateLimitBodyIsNotAProblem() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses['429'].content['application/json']"
                        + ".schema.$ref", is("#/components/schemas/RateLimited")))
                .andExpect(jsonPath("$.components.schemas.RateLimited.properties", hasKey("error")))
                .andExpect(jsonPath("$.components.examples.RateLimitedExample.value.error", is("Too many requests")));
    }

    @Test
    @DisplayName("the Problem schema is published with the optional errors member")
    void problemSchemaIsPublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.Problem.properties", hasKey("detail")))
                .andExpect(jsonPath("$.components.schemas.Problem.properties", hasKey("errors")));
    }
}
