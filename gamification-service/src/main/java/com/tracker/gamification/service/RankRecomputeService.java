package com.tracker.gamification.service;

public interface RankRecomputeService {

    /**
     * Re-ranks every tracked user by total XP and rewrites their UserRank snapshot.
     *
     * <p>Boxed, not primitive int -- issue #82's @SchedulerLock proxy on the implementation
     * needs a nullable return type to represent "lock not acquired, skipped this tick".
     *
     * @return the number of users ranked
     */
    Integer recompute();
}
