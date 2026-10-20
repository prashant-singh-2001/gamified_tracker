package com.tracker.gamification.dto;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.domain.RankMovement;

import java.time.LocalDateTime;

/**
 * The caller's rank. {@code percentile} is a TOP fraction: 0 is the best.
 *
 * <p>Since issue 135 a rank is a percentile of {@code rankScore} (XP weighted by recency, halving every
 * 14 days by default), not of lifetime XP, so {@code tier}, {@code division}, {@code percentile},
 * {@code position} and the cutoff and nudge fields all follow recent form. {@code totalXp} stays the
 * lifetime figure. Everything after {@code updatedAt} is computed from the same snapshot, so it is at most
 * one recompute (30 seconds by default) stale and approximate by nature: other users earning XP moves
 * every boundary.
 *
 * @param ranked           always {@code true} on a card; present so a client can parse this and the
 *                         {@code {"ranked": false}} answer to {@code ?allowUnranked=true} uniformly
 * @param tierCutoffXp     the lowest rank score currently held by anyone in the caller's tier: the
 *                         effective floor for staying in it
 * @param nextTier         the next BETTER tier that currently has members (an empty tier cannot be
 *                         reached by earning XP, so it is skipped); {@code null} at the top
 * @param xpToNextTier     score needed to match the lowest member of {@code nextTier}; {@code null} when
 *                         {@code nextTier} is {@code null}. XP earned now adds to the score one for one
 * @param rankScore        the recency-weighted score the rank is computed from
 * @param division         1 (best) to 3 inside the tier; each tier band is cut into equal thirds
 * @param divisionProgress how far up the current division the caller sits: 0 about to drop, 1 about to
 *                         be promoted. For a progress bar
 * @param nextDivisionTier the tier of the next BETTER populated division (the same tier, or the one above
 *                         from division 1); {@code null} at the very top
 * @param nextDivision     that division's number; {@code null} when {@code nextDivisionTier} is
 * @param xpToNextDivision score needed to match the lowest member of that division
 * @param positionChange   places gained since the movement baseline (about a day ago by default); positive
 *                         is up the table; {@code null} when there is no baseline yet
 * @param previousTier     tier at the baseline, {@code null} when there is none
 * @param previousDivision division at the baseline, {@code null} when there is none
 * @param trend            NEW (no baseline), PROMOTED / DEMOTED (tier or division changed), or UP / DOWN /
 *                         SAME (same step, position moved)
 */
public record RankCardDto(RankTier tier, int overallLevel, double totalXp, double percentile,
                           int position, int totalUsers, LocalDateTime updatedAt,
                           boolean ranked, Double tierCutoffXp, RankTier nextTier, Double xpToNextTier,
                           double rankScore, int division, double divisionProgress,
                           RankTier nextDivisionTier, Integer nextDivision, Double xpToNextDivision,
                           Integer positionChange, RankTier previousTier, Integer previousDivision,
                           RankMovement.Trend trend) {
}
