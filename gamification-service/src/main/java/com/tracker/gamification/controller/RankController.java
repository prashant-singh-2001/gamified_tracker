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
import java.util.Optional;

@AllArgsConstructor
@RestController
@RequestMapping("/ranks")
public class RankController {

    private final RankService rankService;
    private final RankRecomputeService rankRecomputeService;

    // Until the first recompute has seen the caller, there is no card. By default that is a 404, which
    // is what existing clients key on. allowUnranked=true is an OPT-IN to the friendlier answer --
    // 200 {"ranked": false} -- so a client can stop special-casing a 404 as "not an error". The
    // default can be flipped once the frontend has moved; flipping it first would break them.
    @GetMapping("/me")
    public ResponseEntity<?> getMyRank(@RequestHeader("userId") Long userId,
                                       @RequestParam(name = "allowUnranked", defaultValue = "false") boolean allowUnranked) {
        Optional<RankCardDto> card = rankService.getRankCard(userId);
        if (card.isPresent()) {
            return ResponseEntity.ok(card.get());
        }
        if (allowUnranked) {
            return ResponseEntity.ok(Map.of("ranked", false));
        }
        throw new NoSuchElementException("No rank yet for userId: " + userId);
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
