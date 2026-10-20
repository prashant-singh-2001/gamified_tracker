package com.tracker.gamification.dto;

import com.tracker.gamification.dao.RankTier;

/** The lowest rank score held by any member of one division of a tier. Boxed types: see UserXpProjection. */
public interface DivisionFloorProjection {
    RankTier getTier();

    Integer getDivision();

    Double getMinScore();
}
