package com.tracker.gamification.controller;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.domain.RankMovement;
import com.tracker.gamification.dto.RankCardDto;
import com.tracker.gamification.dto.RankDistributionDto;
import com.tracker.gamification.dto.RankTierMemberDto;
import com.tracker.gamification.service.RankRecomputeService;
import com.tracker.gamification.service.RankService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RankController.class)
@DisplayName("Rank Controller Tests")
public class RankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankService rankService;

    @MockitoBean
    private RankRecomputeService rankRecomputeService;

    @Test
    @DisplayName("GET /ranks/me reads the caller's id from the userId header and returns their card")
    void testGetMyRank() throws Exception {
        var card = new RankCardDto(RankTier.SUMMIT, 5, 5000.0, 0.02, 1, 50, LocalDateTime.now(), true, null, null, null,
                5000.0, 1, 0.5, null, null, null, null, null, null, RankMovement.Trend.NEW);
        when(rankService.getRankCard(7L)).thenReturn(Optional.of(card));

        mockMvc.perform(get("/ranks/me").header("userId", 7L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("SUMMIT"))
                .andExpect(jsonPath("$.overallLevel").value(5));

        verify(rankService).getRankCard(7L);
    }

    @Test
    @DisplayName("GET /ranks/me returns 404 when the caller has no snapshot yet")
    void testGetMyRank_notYetRanked() throws Exception {
        when(rankService.getRankCard(8L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/ranks/me").header("userId", 8L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /ranks/me?allowUnranked=true answers 200 {ranked:false} instead of 404 -- opt-in only")
    void testGetMyRank_notYetRanked_optInAnswersWithRankedFalse() throws Exception {
        when(rankService.getRankCard(8L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/ranks/me").param("allowUnranked", "true").header("userId", 8L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranked").value(false))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("allowUnranked=false (explicit) is still the 404 existing clients rely on")
    void testGetMyRank_notYetRanked_explicitFalseStaysA404() throws Exception {
        when(rankService.getRankCard(8L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/ranks/me").param("allowUnranked", "false").header("userId", 8L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a ranked caller gets the full card -- percentile still a top fraction -- plus the rank nudge")
    void testGetMyRank_includesTheRankNudge() throws Exception {
        var card = new RankCardDto(RankTier.ALPINE, 4, 500.0, 0.3, 12, 40, LocalDateTime.now(),
                true, 400.0, RankTier.PEAK, 1500.0,
                500.0, 2, 0.4, RankTier.ALPINE, 1, 120.0, 3, RankTier.ALPINE, 3, RankMovement.Trend.UP);
        when(rankService.getRankCard(7L)).thenReturn(Optional.of(card));

        mockMvc.perform(get("/ranks/me").param("allowUnranked", "true").header("userId", 7L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranked").value(true))
                .andExpect(jsonPath("$.tier").value("ALPINE"))
                .andExpect(jsonPath("$.percentile").value(0.3))
                .andExpect(jsonPath("$.tierCutoffXp").value(400.0))
                .andExpect(jsonPath("$.nextTier").value("PEAK"))
                .andExpect(jsonPath("$.xpToNextTier").value(1500.0))
                .andExpect(jsonPath("$.rankScore").value(500.0))
                .andExpect(jsonPath("$.division").value(2))
                .andExpect(jsonPath("$.divisionProgress").value(0.4))
                .andExpect(jsonPath("$.nextDivisionTier").value("ALPINE"))
                .andExpect(jsonPath("$.nextDivision").value(1))
                .andExpect(jsonPath("$.xpToNextDivision").value(120.0))
                .andExpect(jsonPath("$.positionChange").value(3))
                .andExpect(jsonPath("$.previousTier").value("ALPINE"))
                .andExpect(jsonPath("$.previousDivision").value(3))
                .andExpect(jsonPath("$.trend").value("UP"));
    }

    @Test
    @DisplayName("at the top tier there is no next tier: nextTier and xpToNextTier are null, keys present")
    void testGetMyRank_topTierHasNoNextTier() throws Exception {
        var card = new RankCardDto(RankTier.SUMMIT, 9, 9000.0, 0.0, 1, 40, LocalDateTime.now(),
                true, 8000.0, null, null,
                9000.0, 1, 0.5, null, null, null, null, null, null, RankMovement.Trend.NEW);
        when(rankService.getRankCard(7L)).thenReturn(Optional.of(card));

        mockMvc.perform(get("/ranks/me").header("userId", 7L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextTier").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.xpToNextTier").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.tierCutoffXp").value(8000.0));
    }

    @Test
    @DisplayName("GET /ranks/{tier}/leaderboard returns that tier's members")
    void testGetTierLeaderboard() throws Exception {
        var member = new RankTierMemberDto(1, 1L, 900.0, 5, RankTier.SUMMIT,
                880.0, 1, 2, RankMovement.Trend.UP);
        when(rankService.getTierLeaderboard(RankTier.SUMMIT, 0, 20)).thenReturn(List.of(member));

        mockMvc.perform(get("/ranks/SUMMIT/leaderboard").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1L))
                .andExpect(jsonPath("$[0].withinRankPosition").value(1));

        verify(rankService).getTierLeaderboard(RankTier.SUMMIT, 0, 20);
    }

    @Test
    @DisplayName("GET /ranks/me/leaderboard resolves the caller's own tier first")
    void testGetMyTierLeaderboard() throws Exception {
        var card = new RankCardDto(RankTier.PEAK, 3, 700.0, 0.1, 2, 20, LocalDateTime.now(), true, null, null, null,
                700.0, 1, 0.5, null, null, null, null, null, null, RankMovement.Trend.NEW);
        when(rankService.getRankCard(9L)).thenReturn(Optional.of(card));
        when(rankService.getTierLeaderboard(RankTier.PEAK, 0, 20)).thenReturn(List.of());

        mockMvc.perform(get("/ranks/me/leaderboard").header("userId", 9L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(rankService).getTierLeaderboard(RankTier.PEAK, 0, 20);
    }

    @Test
    @DisplayName("GET /ranks returns the tier distribution")
    void testGetDistribution() throws Exception {
        when(rankService.getDistribution()).thenReturn(List.of(new RankDistributionDto(RankTier.SUMMIT, 5L)));

        mockMvc.perform(get("/ranks").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tier").value("SUMMIT"))
                .andExpect(jsonPath("$[0].userCount").value(5));
    }

    @Test
    @DisplayName("POST /ranks/recompute triggers a recompute and reports how many users were ranked")
    void testRecompute() throws Exception {
        when(rankRecomputeService.recompute()).thenReturn(42);

        mockMvc.perform(post("/ranks/recompute").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankedUsers").value(42));

        verify(rankRecomputeService).recompute();
    }
}
