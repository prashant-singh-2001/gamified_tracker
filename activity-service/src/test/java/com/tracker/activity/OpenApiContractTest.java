package com.tracker.activity;

import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Pins what the published /v3/api-docs promises, so the contract in the generated spec cannot drift
// from the code unnoticed. The spec is generated from the real controllers and DTOs.
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    // Same reason as ActivityServiceApplicationTests: there is no shedlock table in the H2 schema.
    @MockBean
    private LockProvider lockProvider;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("the natural-language draft status lists DISABLED and documents it as a stable state")
    void draftStatusDocumentsDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.NaturalLogDraftResponse.properties.status.enum",
                        containsInAnyOrder("PARSED", "NEEDS_CLARIFICATION", "DISABLED", "UNAVAILABLE")))
                .andExpect(jsonPath("$.components.schemas.NaturalLogDraftResponse.properties.status.description",
                        containsString("DISABLED")));
    }

    @Test
    @DisplayName("POST /activitylog/natural documents the always-200 DISABLED contract with an example")
    void naturalEndpointDocumentsDisabledExample() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/activitylog/natural'].post.description",
                        containsString("DISABLED")))
                .andExpect(jsonPath("$.paths['/activitylog/natural'].post.responses['200'].content['application/json']"
                        + ".examples.DISABLED.value.status", org.hamcrest.Matchers.is("DISABLED")))
                .andExpect(jsonPath("$.paths['/activitylog/natural'].post.responses['200'].content['application/json']"
                        + ".examples.DISABLED.value.draft", org.hamcrest.Matchers.nullValue()));
    }
}
