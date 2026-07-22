package com.tracker.gamification.service;

public interface RankRecomputeService {

    /**
     * Re-ranks every tracked user by total XP and rewrites their UserRank snapshot.
     *
     * @return the number of users ranked
     */
    int recompute();
}
