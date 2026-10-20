package com.tracker.gamification.dto;

import com.tracker.gamification.dao.RankTier;

import java.time.LocalDateTime;

/**
 * The caller's rank. {@code percentile} is a TOP fraction: 0 is the best. The last four components
 * are additive (a rank nudge for the result screen) and computed from the same snapshot as the rest,
 * so they are at most one recompute (5 minutes) stale and approximate by nature: other users earning
 * XP moves every boundary.
 *
 * @param ranked        always {@code true} on a card; present so a client can parse this and the
 *                      {@code {"ranked": false}} answer to {@code ?allowUnranked=true} uniformly
 * @param tierCutoffXp  the lowest XP currently held by anyone in the caller's tier -- the effective
 *                      floor for staying in it
 * @param nextTier      the next BETTER tier that currently has members (an empty tier cannot be
 *                      reached by earning XP, so it is skipped); {@code null} at the top (SUMMIT)
 * @param xpToNextTier  XP needed to match the lowest member of {@code nextTier}; {@code null} when
 *                      {@code nextTier} is {@code null}
 */
public record RankCardDto(RankTier tier, int overallLevel, double totalXp, double percentile,
                           int position, int totalUsers, LocalDateTime updatedAt,
                           boolean ranked, Double tierCutoffXp, RankTier nextTier, Double xpToNextTier) {
}
