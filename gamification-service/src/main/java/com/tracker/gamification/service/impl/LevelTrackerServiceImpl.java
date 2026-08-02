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
import com.tracker.gamification.service.AchievementService;
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
    private final AchievementService achievementService;

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
        boolean onDefaultCurve = applyLevel(tracker);

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

        // Badge criteria are all functions of state this method just changed (total XP, max level,
        // log count), so this is the one place where every one of them can newly become true.
        // Runs in the same transaction on purpose: grantIfAbsent is an idempotent ON CONFLICT
        // upsert, so a rollback-and-redeliver from the RabbitMQ path re-evaluates safely rather
        // than double-granting. Awards are read back via GET /achievements, not returned here —
        // the HTTP and RabbitMQ callers of save() want different things from the response.
        achievementService.evaluateAndAward(userId);

        return mapToDto(saved, leveledUp, progressFor(saved, onDefaultCurve));
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

    /**
     * Whether this activity resolves levels from the default curve rather than its own explicit
     * rows. Carried alongside the outcome so save() can reuse the answer for progress instead of
     * asking the database the same question twice in one transaction.
     */
    private record LevelResolution(LevelOutcome outcome, boolean usedDefaultCurve) {
    }

    /** @return true when the level came from the default curve. */
    private boolean applyLevel(LevelTracker levelTracker) {
        LevelResolution resolution = resolveLevel(levelTracker.getActivityId(), levelTracker.getTotalXp());

        if (resolution.outcome() instanceof LevelOutcome.LeveledUp up) {
            levelTracker.setLevel(up.level());
            levelTracker.setCurrentLevelXp(up.currentLevelXp());
        } else if (resolution.outcome() instanceof LevelOutcome.InProgress ip) {
            levelTracker.setLevel(ip.level());
            levelTracker.setCurrentLevelXp(ip.currentLevelXp());
        }

        return resolution.usedDefaultCurve();
    }

    // NOTE on meaning: LeveledUp here means "an explicit activity_level_threshold row was reached" —
    // it does NOT mean the user's level increased on this save (save() computes that separately by
    // comparing against the level captured before mutation). InProgress covers everything else:
    // genuinely below the activity's own next explicit threshold, OR resolved via the default curve
    // when the activity has no explicit rows at all.
    private LevelResolution resolveLevel(Long activityId, double totalXp) {
        var reachedLevels = activityLevelThresholdRepository.findReachedLevels(
                activityId, totalXp, PageRequest.of(0, 1));

        if (!reachedLevels.isEmpty()) {
            var reached = reachedLevels.get(0);
            return new LevelResolution(new LevelOutcome.LeveledUp(
                    reached.getId().getLevel(),
                    totalXp - reached.getXpRequired()), false);
        }

        // No explicit threshold reached. Only fall back to the default curve when this activity has
        // NO threshold rows at all — an activity with even one row must stay on its own data forever,
        // never blend in formula-derived levels (see the precedence decision in
        // RANK_AND_LEVEL_SYSTEM_TODO.md). countForActivity is queried only on this empty-result path,
        // so the common explicit-threshold case costs exactly the same number of queries as before.
        boolean useDefaultCurve = levelCurve.isEnabled()
                && activityLevelThresholdRepository.countForActivity(activityId) == 0;

        if (useDefaultCurve) {
            return new LevelResolution(new LevelOutcome.InProgress(
                    levelCurve.levelFor(totalXp),
                    levelCurve.currentLevelXpFor(totalXp)), true);
        }

        return new LevelResolution(new LevelOutcome.InProgress(1, totalXp), false);
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
        return progressFor(tracker, null);
    }

    /**
     * @param knownCurveUsage non-null when the caller has already established whether this activity
     *                        is on the default curve (save() learns it from applyLevel). Passing it
     *                        keeps the query count identical to before progress became curve-aware;
     *                        null means "work it out", for the read paths that never resolved a level.
     */
    private LevelProgress progressFor(LevelTracker tracker, Boolean knownCurveUsage) {
        var nextLevels = activityLevelThresholdRepository.findNextLevels(
                tracker.getActivityId(), tracker.getTotalXp(), PageRequest.of(0, 1));

        if (!nextLevels.isEmpty()) {
            return LevelProgress.toward(tracker.getTotalXp(), tracker.getCurrentLevelXp(),
                    nextLevels.get(0).getXpRequired());
        }

        // No explicit next row. Mirror resolveLevel's precedence exactly: fall back to the curve
        // only when the activity has NO explicit rows at all, so progress and level always agree
        // about which ladder this activity is on.
        boolean useDefaultCurve = knownCurveUsage != null
                ? knownCurveUsage
                : levelCurve.isEnabled()
                        && activityLevelThresholdRepository.countForActivity(tracker.getActivityId()) == 0;

        return useDefaultCurve ? curveProgress(tracker) : LevelProgress.MAX_LEVEL;
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

        if (next.isPresent()) {
            return LevelProgress.toward(tracker.getTotalXp(), tracker.getCurrentLevelXp(),
                    next.get().getXpRequired());
        }

        // `thresholds` is this activity's COMPLETE row set (findAllForActivities), so empty here
        // means the same thing countForActivity checks in progressFor — no extra query needed,
        // which is the whole point of the batched path.
        boolean useDefaultCurve = levelCurve.isEnabled() && thresholds.isEmpty();

        return useDefaultCurve ? curveProgress(tracker) : LevelProgress.MAX_LEVEL;
    }

    /**
     * Progress toward the next level on the default curve, for activities with no explicit
     * thresholds. The curve's band boundaries are xpRequiredFor(level) .. xpRequiredFor(level + 1),
     * which is exactly the pair LevelProgress.toward expects.
     */
    private LevelProgress curveProgress(LevelTracker tracker) {
        Integer current = tracker.getLevel();
        int level = current == null ? 1 : current;

        if (level >= levelCurve.getMaxLevel()) {
            return LevelProgress.MAX_LEVEL;
        }

        return LevelProgress.toward(tracker.getTotalXp(), tracker.getCurrentLevelXp(),
                levelCurve.xpRequiredFor(level + 1));
    }

    // Not yet called from save() — wiring live overall-level feedback into the XP transaction
    // (TODO §B.7) needs a partial UserRank update (totalXp + overallLevel only, leaving
    // rank/percentile for the next batch), which is a separate decision from this delegation fix.
    int overallLevelFor(double totalXp) {
        return overallLevelService.overallLevelFor(totalXp);
    }
    
}
