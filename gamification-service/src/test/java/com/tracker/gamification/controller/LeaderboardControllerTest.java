package com.tracker.gamification.controller;

import com.tracker.gamification.dto.LeaderboardEntryDto;
import com.tracker.gamification.service.LeaderboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaderboardController.class)
@DisplayName("Leaderboard Controller Tests")
public class LeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaderboardService leaderboardService;

    @Test
    @DisplayName("GET /leaderboard returns the global leaderboard for the given page/size")
    void testGetLeaderboard() throws Exception {
        var entry1 = new LeaderboardEntryDto(1L, 1L, 500.0);
        var entry2 = new LeaderboardEntryDto(2L, 2L, 300.0);

        when(leaderboardService.getGlobalLeaderboard(0, 10))
                .thenReturn(List.of(entry1, entry2));

        mockMvc.perform(get("/leaderboard")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].userId").value(1L))
                .andExpect(jsonPath("$[0].totalXp").value(500.0))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[1].userId").value(2L));

        verify(leaderboardService).getGlobalLeaderboard(0, 10);
    }

    @Test
    @DisplayName("GET /leaderboard without page/size is rejected as a missing required parameter")
    void testGetLeaderboard_missingRequiredParams() throws Exception {
        mockMvc.perform(get("/leaderboard").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /leaderboard/activity/{activityId} returns that activity's leaderboard")
    void testGetActivityLeaderboard() throws Exception {
        var entry = new LeaderboardEntryDto(1L, 5L, 200.0);

        when(leaderboardService.getActivityLeaderboard(3L, 0, 10))
                .thenReturn(List.of(entry));

        mockMvc.perform(get("/leaderboard/activity/3")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].userId").value(5L))
                .andExpect(jsonPath("$[0].totalXp").value(200.0));

        verify(leaderboardService).getActivityLeaderboard(3L, 0, 10);
    }

    @Test
    @DisplayName("GET /leaderboard/me reads the caller's id from the userId header, not a path/body value")
    void testGetMyRank_readsUserIdHeader() throws Exception {
        when(leaderboardService.getMyRank(7L)).thenReturn(3L);

        mockMvc.perform(get("/leaderboard/me")
                        .header("userId", 7L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));

        verify(leaderboardService).getMyRank(eq(7L));
    }
}
