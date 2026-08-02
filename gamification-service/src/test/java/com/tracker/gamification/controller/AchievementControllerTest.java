package com.tracker.gamification.controller;

import com.tracker.gamification.dto.UserAchievementDto;
import com.tracker.gamification.service.AchievementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AchievementController.class)
@DisplayName("Achievement Controller Tests")
class AchievementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AchievementService achievementService;

    @Test
    @DisplayName("returns the caller's unlocked badges")
    void findUnlocked_returnsBadges() throws Exception {
        when(achievementService.findUnlocked(7L)).thenReturn(List.of(
                new UserAchievementDto(5L, "XP_1000", "Grinder", "Earn 1000 XP",
                        "TOTAL_XP", 1000L, null, LocalDateTime.of(2026, 7, 30, 12, 0))));

        mockMvc.perform(get("/achievements").header("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("XP_1000"))
                .andExpect(jsonPath("$[0].name").value("Grinder"))
                .andExpect(jsonPath("$[0].threshold").value(1000));
    }

    @Test
    @DisplayName("scopes to the header user, never a caller-supplied parameter")
    void findUnlocked_scopedToHeaderUser() throws Exception {
        when(achievementService.findUnlocked(7L)).thenReturn(List.of());

        // A userId query param must not be able to redirect the lookup at another user.
        mockMvc.perform(get("/achievements").param("userId", "999").header("userId", 7L))
                .andExpect(status().isOk());

        verify(achievementService).findUnlocked(7L);
        verify(achievementService, never()).findUnlocked(999L);
    }

    @Test
    @DisplayName("400 when the trusted userId header is absent")
    void findUnlocked_requiresHeader() throws Exception {
        mockMvc.perform(get("/achievements"))
                .andExpect(status().isBadRequest());

        verify(achievementService, never()).findUnlocked(anyLong());
    }
}
