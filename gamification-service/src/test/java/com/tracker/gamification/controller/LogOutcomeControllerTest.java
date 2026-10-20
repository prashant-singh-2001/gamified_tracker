package com.tracker.gamification.controller;

import com.tracker.gamification.dto.LogOutcomeDto;
import com.tracker.gamification.service.LogOutcomeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogOutcomeController.class)
@DisplayName("Log Outcome Controller Tests")
public class LogOutcomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogOutcomeService logOutcomeService;

    @Test
    @DisplayName("GET /activitylog/{id}/outcome returns APPLIED with every key, scoped to the header's user")
    void applied() throws Exception {
        when(logOutcomeService.outcomeFor(1L, 42L)).thenReturn(LogOutcomeDto.applied(120.5, true, 3, 2));

        mockMvc.perform(get("/activitylog/42/outcome")
                        .header("userId", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.xpApplied").value(120.5))
                .andExpect(jsonPath("$.leveledUp").value(true))
                .andExpect(jsonPath("$.newLevel").value(3))
                .andExpect(jsonPath("$.previousLevel").value(2));

        verify(logOutcomeService).outcomeFor(1L, 42L);
    }

    @Test
    @DisplayName("PENDING is a 200 with the same five keys, the detail ones null")
    void pending() throws Exception {
        when(logOutcomeService.outcomeFor(1L, 42L)).thenReturn(LogOutcomeDto.pending());

        mockMvc.perform(get("/activitylog/42/outcome")
                        .header("userId", 1L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.xpApplied").doesNotExist())
                .andExpect(jsonPath("$.leveledUp").doesNotExist());
    }

    @Test
    @DisplayName("a non-numeric log id is a 400, not a 500")
    void nonNumericId() throws Exception {
        mockMvc.perform(get("/activitylog/not-a-number/outcome").header("userId", 1L))
                .andExpect(status().isBadRequest());
    }
}
