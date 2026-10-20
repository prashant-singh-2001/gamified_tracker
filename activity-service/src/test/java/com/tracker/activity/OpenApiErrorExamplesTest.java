package com.tracker.activity;

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

    // Same reason as ActivityServiceApplicationTests: there is no shedlock table in the H2 schema.
    @MockBean
    private LockProvider lockProvider;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a shared Problem schema documents detail and the optional errors / suggestions members")
    void problemSchemaIsPublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.Problem.properties", hasKey("detail")))
                .andExpect(jsonPath("$.components.schemas.Problem.properties", hasKey("errors")))
                .andExpect(jsonPath("$.components.schemas.Problem.properties", hasKey("requestedName")))
                .andExpect(jsonPath("$.components.schemas.Problem.properties", hasKey("suggestions")));
    }

    @Test
    @DisplayName("POST /activitylog/ lists its 400, 404 (with suggestions) and 409 bodies")
    void logEndpointDocumentsItsErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/activitylog/'].post.responses", hasKey("400")))
                .andExpect(jsonPath("$.paths['/activitylog/'].post.responses", hasKey("404")))
                .andExpect(jsonPath("$.paths['/activitylog/'].post.responses", hasKey("409")))
                .andExpect(jsonPath("$.paths['/activitylog/'].post.responses['404'].content"
                        + "['application/problem+json'].schema.$ref", is("#/components/schemas/Problem")))
                .andExpect(jsonPath("$.paths['/activitylog/'].post.responses['404'].content"
                        + "['application/problem+json'].examples.ProblemActivityNameNotFound.$ref",
                        is("#/components/examples/ProblemActivityNameNotFound")));
    }

    @Test
    @DisplayName("the unknown-activity example carries requestedName and ranked suggestions, as the handler does")
    void unknownNameExampleShowsSuggestions() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.examples.ProblemActivityNameNotFound.value.status", is(404)))
                .andExpect(jsonPath("$.components.examples.ProblemActivityNameNotFound.value.requestedName",
                        is("Runing")))
                .andExpect(jsonPath("$.components.examples.ProblemActivityNameNotFound.value.suggestions[0].name",
                        is("Running")));
    }

    @Test
    @DisplayName("operations with a path id get a 404, and per-user reads a 403")
    void shapeRules() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/activitylog/{id}'].get.responses", hasKey("404")))
                .andExpect(jsonPath("$.paths['/activitylog/user/{id}'].get.responses", hasKey("403")))
                .andExpect(jsonPath("$.paths['/activitylog/user/{id}'].get.responses", hasKey("200")));
    }
}
