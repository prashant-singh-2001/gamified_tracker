package com.tracker.gamification;

import net.javacrumbs.shedlock.core.LockProvider;
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

// Pins that the published /v3/api-docs describes the error bodies, not just the happy paths.
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiErrorExamplesTest {

    // Same reason as GamificationServiceApplicationTests: there is no shedlock table in the H2 schema.
    @MockBean
    private LockProvider lockProvider;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a shared Problem schema documents detail and the optional errors member")
    void problemSchemaIsPublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.Problem.properties", hasKey("detail")))
                .andExpect(jsonPath("$.components.schemas.Problem.properties", hasKey("errors")));
    }

    @Test
    @DisplayName("per-user reads document 403, id reads document 404, and bodies document 400")
    void shapeAndSpecificRules() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/level/user/{userId}'].get.responses", hasKey("403")))
                .andExpect(jsonPath("$.paths['/level/{id}'].get.responses", hasKey("404")))
                .andExpect(jsonPath("$.paths['/notifications/{id}/read'].post.responses", hasKey("404")))
                .andExpect(jsonPath("$.paths['/threshold'].post.responses", hasKey("400")))
                .andExpect(jsonPath("$.paths['/level/{id}'].get.responses['404'].content"
                        + "['application/problem+json'].schema.$ref", is("#/components/schemas/Problem")));
    }

    @Test
    @DisplayName("the examples match what the handlers actually return")
    void examplesArePublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.examples.ProblemForbidden.value.status", is(403)))
                .andExpect(jsonPath("$.components.examples.ProblemNotFound.value.status", is(404)))
                .andExpect(jsonPath("$.components.examples.ProblemThresholdCurve.value.status", is(409)));
    }
}
