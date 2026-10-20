package com.tracker.gamification.service;

import com.tracker.gamification.dto.LogOutcomeDto;

public interface LogOutcomeService {

    /**
     * What applying the given log's XP did, as seen by {@code callerUserId}. Never throws for an
     * unknown or someone else's log: both read as PENDING, so the endpoint cannot be used to probe
     * which log ids exist or who owns them.
     */
    LogOutcomeDto outcomeFor(Long callerUserId, Long logId);
}
