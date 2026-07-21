package com.tracker.gamification.service;

import com.tracker.gamification.dto.LeaderboardEntryDto;

import java.util.List;

public interface LeaderboardService {

    List<LeaderboardEntryDto> getGlobalLeaderboard(int page, int size);

    List<LeaderboardEntryDto> getActivityLeaderboard(Long activityId, int page, int size);

    Long getMyRank(Long userId);
}
