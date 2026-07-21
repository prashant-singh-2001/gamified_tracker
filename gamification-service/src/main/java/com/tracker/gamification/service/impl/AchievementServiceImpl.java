package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.Achievement;
import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.repository.AchievementRepository;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.repository.UserAchievementRepository;
import com.tracker.gamification.service.AchievementService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Transactional
public class AchievementServiceImpl implements AchievementService {

    private final LevelTrackerRepository levelTrackerRepository;
    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;

    @Override
    public List<Achievement> evaluateAndAward(Long userId) {
        double totalXp = levelTrackerRepository.getTotalXpByUserId(userId);
        List<LevelTracker> trackers = levelTrackerRepository.findAllByUserId(userId);

        int maxLevel = trackers.stream()
                .mapToInt(t -> t.getLevel() == null ? 0 : t.getLevel())
                .max()
                .orElse(0);

        long activitiesLogged = trackers.stream()
                .mapToInt(LevelTracker::getLogCount)
                .sum();

        Map<Long, Integer> levelByActivityId = trackers.stream()
                .collect(Collectors.toMap(LevelTracker::getActivityId,
                        t -> t.getLevel() == null ? 0 : t.getLevel()));

        List<Achievement> newlyUnlocked = new ArrayList<>();
        for (Achievement achievement : achievementRepository.findByActiveTrue()) {
            boolean satisfied = switch (achievement.getCriteriaType()) {
                case TOTAL_XP -> totalXp >= achievement.getThreshold();
                case REACH_LEVEL_ANY -> maxLevel >= achievement.getThreshold();
                case ACTIVITIES_LOGGED -> activitiesLogged >= achievement.getThreshold();
                case ACTIVITY_LEVEL -> levelByActivityId.getOrDefault(achievement.getActivityId(), 0)
                        >= achievement.getThreshold();
            };

            if (satisfied && userAchievementRepository.grantIfAbsent(userId, achievement.getId()) == 1) {
                newlyUnlocked.add(achievement);
            }
        }

        return newlyUnlocked;
    }
}
