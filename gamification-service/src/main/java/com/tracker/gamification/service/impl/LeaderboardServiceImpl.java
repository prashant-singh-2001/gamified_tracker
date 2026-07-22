package com.tracker.gamification.service.impl;

import com.tracker.gamification.dto.LeaderboardEntryDto;
import com.tracker.gamification.dto.UserXpProjection;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.service.LeaderboardService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
@AllArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private final LevelTrackerRepository levelTrackerRepository;

    @Override
    public List<LeaderboardEntryDto> getGlobalLeaderboard(int page, int size) {
        List<UserXpProjection> ranking =
                levelTrackerRepository.findGlobalRanking(PageRequest.of(page, size));

        int offset = page * size;
        return IntStream.range(0, ranking.size())
                .mapToObj(i -> mapToDto(ranking.get(i), offset + i + 1))
                .toList();
    }

    @Override
    public List<LeaderboardEntryDto> getActivityLeaderboard(Long activityId, int page, int size) {
        List<UserXpProjection> ranking =
                levelTrackerRepository.findActivityRanking(activityId, PageRequest.of(page, size));

        long offset = (long) page * size;
        return IntStream.range(0, ranking.size())
                .mapToObj(i -> mapToDto(ranking.get(i), offset + i + 1))
                .toList();
    }

    @Override
    public Long getMyRank(Long userId) {
        double myXp = levelTrackerRepository.getTotalXpByUserId(userId); // COALESCE(...,0), never null
        long ahead = levelTrackerRepository.countUsersAhead(myXp);
        return (ahead + 1);
    }

    private LeaderboardEntryDto mapToDto(UserXpProjection projection, long rank) {
        double totalXp = projection.getTotalXp() != null ? projection.getTotalXp() : 0.0;
        return new LeaderboardEntryDto(rank, projection.getUserId(), totalXp);
    }
}
