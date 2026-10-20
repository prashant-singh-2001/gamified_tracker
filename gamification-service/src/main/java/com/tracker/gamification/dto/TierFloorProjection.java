package com.tracker.gamification.dto;

import com.tracker.gamification.dao.RankTier;

/** The lowest XP held by any member of a tier. Boxed {@code Double}: see UserXpProjection. */
public interface TierFloorProjection {
    RankTier getTier();

    Double getMinTotalXp();
}
