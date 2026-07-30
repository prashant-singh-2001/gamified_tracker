package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.ActivityLevelThreshold;
import com.tracker.gamification.dao.LevelTracker;
import com.tracker.gamification.dao.LevelTrackerArchive;
import com.tracker.gamification.dao.LevelUpEvent;
import com.tracker.gamification.domain.LevelCurve;
import com.tracker.gamification.domain.LevelOutcome;
import com.tracker.gamification.domain.LevelProgress;
import com.tracker.gamification.dto.LevelTrackerDto;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.repository.ActivityLevelThresholdRepository;
import com.tracker.gamification.repository.LevelTrackerArchiveRepository;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.repository.LevelUpEventRepository;
import com.tracker.gamification.service.LevelTrackerService;
import com.tracker.gamification.service.OverallLevelService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Transactional
public class LevelTrackerServiceImpl implements LevelTrackerService {

    private final LevelTrackerRepository levelTrackerRepository;
    private final ActivityLevelThresholdRepository activityLevelThresholdRepository;
    private final LevelTrackerArchiveRepository levelTrackerArchiveRepository;
    private final LevelUpEventRepository levelUpEventRepository;
    private final OverallLevelService overallLevelService;
    private final LevelCurve levelCurve;

    @Override
    public List<LevelTrackerDto> findByUserId(Long userId) {
        // OLD: return levelTrackerRepository.findAllByUserId(userId).stream().map(this::mapToDto).toList();
        return mapAll(levelTrackerRepository.findAllByUserId(userId));
    }

    @Override
    public List<LevelTrackerDto> findByActivityId(Long activityId) {
        // OLD: return levelTrackerRepository.findAllByActivityId(activityId).stream().map(this::mapToDto).toList();
        return mapAll(levelTrackerRepository.findAllByActivityId(activityId));
    }

    @Override
    public List<LevelTrackerDto> findAll() {
        // OLD: return levelTrackerRepository.findAll().stream().map(this::mapToDto).toList();
        return mapAll(levelTrackerRepository.findAll());
    }

    @Override
    public LevelTrackerDto findById(Long id) {
        var levelTracker = levelTrackerRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "LevelTracker with id: " + id + " not found"
                        )
                );

        return mapToDto(levelTracker);
    }

    // IDOR fix: userId now passed explicitly from the trusted header, not read off the DTO.
    // @Override
    // public LevelTrackerDto save(LevelTrackerRequestDTO dto) {
    //     boolean created = levelTrackerRepository.insertIfAbsent(dto.userId(), dto.activityId()) == 1;
    //
    //     var tracker = levelTrackerRepository
    //             .findByUserIdAndActivityIdForUpdate(dto.userId(), dto.activityId())
    //             .orElseThrow(); // guaranteed to exist; row is now locked for the rest of this transaction
    //
    //     if (!created) {
    //         archivePreviousState(tracker);
    //     }
    //
    //     tracker.setTotalXp(tracker.getTotalXp() + dto.xp());
    //     applyLevel(tracker);
    //
    //     return mapToDto(levelTrackerRepository.save(tracker));
    // }
    @Override
    public LevelTrackerDto save(Long userId, LevelTrackerRequestDTO dto) {
        boolean created = levelTrackerRepository.insertIfAbsent(userId, dto.activityId()) == 1;

        var tracker = levelTrackerRepository
                .findByUserIdAndActivityIdForUpdate(userId, dto.activityId())
                .orElseThrow(); // guaranteed to exist; row is now locked for the rest of this transaction

        if (!created) {
            archivePreviousState(tracker);
        }

        Integer oldLevel = tracker.getLevel();
        int previousLevel = oldLevel == null ? 1 : oldLevel;

        tracker.setTotalXp(tracker.getTotalXp() + dto.xp());
        tracker.setLogCount(tracker.getLogCount() + 1);
        applyLevel(tracker);

        // leveledUp is a real comparison against the level BEFORE this save, not a side effect of
        // which LevelOutcome branch resolveLevel took — see resolveLevel's comment for why those
        // two things are no longer the same signal once the default curve is in play.
        boolean leveledUp = tracker.getLevel() > previousLevel;

        var saved = levelTrackerRepository.save(tracker);

        if (leveledUp) {
            levelUpEventRepository.save(LevelUpEvent.builder()
                    .userId(tracker.getUserId())
                    .activityId(tracker.getActivityId())
                    .oldLevel(oldLevel)
                    .newLevel(saved.getLevel())
                    .currentLevelXp(saved.getCurrentLevelXp())
                    .totalXp(saved.getTotalXp())
                    .createdAt(LocalDateTime.now())
                    .read(false)
                    .build());
        }

        return mapToDto(saved, leveledUp, progressFor(saved));
    }

    private void archivePreviousState(LevelTracker tracker) {
        levelTrackerArchiveRepository.save(
                LevelTrackerArchive.builder()
                        .userId(tracker.getUserId())
                        .activityId(tracker.getActivityId())
                        .level(tracker.getLevel())
                        .totalXp(tracker.getTotalXp())
                        .currentLevelXp(tracker.getCurrentLevelXp())
                        .archivedAt(LocalDateTime.now())
                        .build()
        );
    }

    private void applyLevel(LevelTracker levelTracker) {
        LevelOutcome outcome = resolveLevel(levelTracker.getActivityId(), levelTracker.getTotalXp());

        if (outcome instanceof LevelOutcome.LeveledUp up) {
            levelTracker.setLevel(up.level());
            levelTracker.setCurrentLevelXp(up.currentLevelXp());
        } else if (outcome instanceof LevelOutcome.InProgress ip) {
            levelTracker.setLevel(ip.level());
            levelTracker.setCurrentLevelXp(ip.currentLevelXp());
        }
    }

    // NOTE on meaning: LeveledUp here means "an explicit activity_level_threshold row was reached" —
    // it does NOT mean the user's level increased on this save (save() computes that separately by
    // comparing against the level captured before mutation). InProgress covers everything else:
    // genuinely below the activity's own next explicit threshold, OR resolved via the default curve
    // when the activity has no explicit rows at all.
    private LevelOutcome resolveLevel(Long activityId, double totalXp) {
        var reachedLevels = activityLevelThresholdRepository.findReachedLevels(
                activityId, totalXp, PageRequest.of(0, 1));

        if (!reachedLevels.isEmpty()) {
            var reached = reachedLevels.get(0);
            return new LevelOutcome.LeveledUp(
                    reached.getId().getLevel(),
                    totalXp - reached.getXpRequired());
        }

        // No explicit threshold reached. Only fall back to the default curve when this activity has
        // NO threshold rows at all — an activity with even one row must stay on its own data forever,
        // never blend in formula-derived levels (see the precedence decision in
        // DEFAULT_LEVEL_CURVE_TODO.md). countForActivity is queried only on this empty-result path,
        // so the common explicit-threshold case costs exactly the same number of queries as before.
        boolean useDefaultCurve = levelCurve.isEnabled()
                && activityLevelThresholdRepository.countForActivity(activityId) == 0;

        if (useDefaultCurve) {
            return new LevelOutcome.InProgress(
                    levelCurve.levelFor(totalXp),
                    levelCurve.currentLevelXpFor(totalXp));
        }

        return new LevelOutcome.InProgress(1, totalXp);
    }

    // OLD:
// private LevelTrackerDto mapToDto(LevelTracker entity, boolean leveledUp) {
//     return new LevelTrackerDto(
//             entity.getUserId(),
//             entity.getActivityId(),
//             entity.getLevel(),
//             entity.getTotalXp(),
//             entity.getCurrentLevelXp(),
//             leveledUp
//     );
// }
    private LevelTrackerDto mapToDto(LevelTracker entity, boolean leveledUp, LevelProgress progress) {
        return new LevelTrackerDto(
                entity.getUserId(),
                entity.getActivityId(),
                entity.getLevel(),
                entity.getTotalXp(),
                entity.getCurrentLevelXp(),
                progress.xpForNextLevel(),
                progress.progressPercent(),
                leveledUp
        );
    }

    // OLD:
// private LevelTrackerDto mapToDto(LevelTracker entity) {
//     return mapToDto(entity, false);
// }
    private LevelTrackerDto mapToDto(LevelTracker entity) {
        return mapToDto(entity, false, progressFor(entity));
    }

    /**
     * Single-row progress lookup — used by findById/findById-via-mapToDto and save().
     */
    private LevelProgress progressFor(LevelTracker tracker) {
        var nextLevels = activityLevelThresholdRepository.findNextLevels(
                tracker.getActivityId(), tracker.getTotalXp(), PageRequest.of(0, 1));

        if (nextLevels.isEmpty()) {
            return LevelProgress.MAX_LEVEL;
        }
        return LevelProgress.toward(tracker.getTotalXp(), tracker.getCurrentLevelXp(),
                nextLevels.get(0).getXpRequired());
    }

    /**
     * Batched mapper for list endpoints: one threshold query total, not one per tracker.
     */
    private List<LevelTrackerDto> mapAll(List<LevelTracker> trackers) {
        if (trackers.isEmpty()) {
            return List.of();
        }

        var activityIds = trackers.stream().map(LevelTracker::getActivityId).distinct().toList();
        var thresholdsByActivity = activityLevelThresholdRepository.findAllForActivities(activityIds)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getId().getActivityId()));

        return trackers.stream()
                .map(tracker -> mapToDto(tracker, false, progressFrom(
                        thresholdsByActivity.getOrDefault(tracker.getActivityId(), Collections.emptyList()),
                        tracker)))
                .toList();
    }

    /**
     * In-memory version of progressFor(...), given the pre-fetched thresholds for one activity.
     */
    private LevelProgress progressFrom(List<ActivityLevelThreshold> thresholds, LevelTracker tracker) {
        var next = thresholds.stream()
                .filter(t -> t.getXpRequired() > tracker.getTotalXp())
                .min(Comparator.comparingInt(t -> t.getId().getLevel()));

        return next.map(t -> LevelProgress.toward(tracker.getTotalXp(), tracker.getCurrentLevelXp(), t.getXpRequired()))
                .orElse(LevelProgress.MAX_LEVEL);
    }

    // Not yet called from save() — wiring live overall-level feedback into the XP transaction
    // (TODO §B.7) needs a partial UserRank update (totalXp + overallLevel only, leaving
    // rank/percentile for the next batch), which is a separate decision from this delegation fix.
    int overallLevelFor(double totalXp) {
        return overallLevelService.overallLevelFor(totalXp);
    }
    
}
