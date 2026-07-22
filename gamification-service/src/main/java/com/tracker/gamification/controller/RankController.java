package com.tracker.gamification.controller;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dto.RankCardDto;
import com.tracker.gamification.dto.RankDistributionDto;
import com.tracker.gamification.dto.RankTierMemberDto;
import com.tracker.gamification.service.RankRecomputeService;
import com.tracker.gamification.service.RankService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@AllArgsConstructor
@RestController
@RequestMapping("/ranks")
public class RankController {

    private final RankService rankService;
    private final RankRecomputeService rankRecomputeService;

    @GetMapping("/me")
    public ResponseEntity<RankCardDto> getMyRank(@RequestHeader("userId") Long userId) {
        RankCardDto card = rankService.getRankCard(userId)
                .orElseThrow(() -> new NoSuchElementException("No rank yet for userId: " + userId));
        return ResponseEntity.ok(card);
    }

    @GetMapping("/{tier}/leaderboard")
    public ResponseEntity<List<RankTierMemberDto>> getTierLeaderboard(
            @PathVariable("tier") RankTier tier,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(rankService.getTierLeaderboard(tier, page, size));
    }

    @GetMapping("/me/leaderboard")
    public ResponseEntity<List<RankTierMemberDto>> getMyTierLeaderboard(
            @RequestHeader("userId") Long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        RankTier tier = rankService.getRankCard(userId)
                .map(RankCardDto::tier)
                .orElseThrow(() -> new NoSuchElementException("No rank yet for userId: " + userId));
        return ResponseEntity.ok(rankService.getTierLeaderboard(tier, page, size));
    }

    @GetMapping
    public ResponseEntity<List<RankDistributionDto>> getDistribution() {
        return ResponseEntity.ok(rankService.getDistribution());
    }

    @PostMapping("/recompute")
    public ResponseEntity<Map<String, Integer>> recompute() {
        int rankedUsers = rankRecomputeService.recompute();
        return ResponseEntity.ok(Map.of("rankedUsers", rankedUsers));
    }
}
