package com.tracker.gamification.service.impl;

import com.tracker.gamification.config.RankingProperties;
import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import com.tracker.gamification.domain.RankScore;
import com.tracker.gamification.dto.TimedXpProjection;
import com.tracker.gamification.dto.UserXpProjection;
import com.tracker.gamification.repository.LevelTrackerRepository;
import com.tracker.gamification.repository.LogXpOutcomeRepository;
import com.tracker.gamification.repository.ManualXpAwardRepository;
import com.tracker.gamification.repository.UserRankRepository;
import com.tracker.gamification.service.OverallLevelService;
import com.tracker.gamification.service.RankRecomputeService;
import lombok.AllArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RankRecomputeServiceImpl implements RankRecomputeService {

    private static final Logger log = LoggerFactory.getLogger(RankRecomputeServiceImpl.class);

    private final LevelTrackerRepository levelTrackerRepository;
    private final UserRankRepository userRankRepository;
    private final OverallLevelService overallLevelService;
    private final LogXpOutcomeRepository logXpOutcomeRepository;
    private final ManualXpAwardRepository manualXpAwardRepository;
    private final RankingProperties rankingProperties;
    private final Clock clock;

    // One user as the ranking sees them: lifetime XP (what totalXp reports) and the recency-decayed score
    // the position is decided by.
    private record Row(Long userId, double totalXp, double score) {
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${ranking.recompute-interval-ms:30000}")
    // Issue #82: same class of race as OutboxRelay -- with N instances every one would rebuild
    // the leaderboard on its own tick. lockAtMostFor stays under the recompute interval
    // so a crashed instance's lock never blocks the next legitimate tick. Since @SchedulerLock is
    // a Spring AOP interceptor on this bean method, it also guards the on-demand
    // POST /ranks/recompute path (LevelTrackerController -> this same method) -- acceptable, that
    // endpoint is rare and admin-triggered, and serializing it with the scheduled run is correct.
    @SchedulerLock(name = "rankRecompute_recompute", lockAtMostFor = "${ranking.lock-at-most-for:PT25S}",
            lockAtLeastFor = "PT5S")
    public Integer recompute() {
        List<UserXpProjection> lifetime = levelTrackerRepository.findAllUserTotals();
        int totalUsers = lifetime.size();

        if (totalUsers == 0) {
            log.debug("Rank recompute skipped: no tracked users yet");
            return 0;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, Double> scores = recencyScores(now);

        // Fluid ranks (issue 135): the order is the recency-decayed score, with lifetime XP breaking ties so a
        // crowd of lapsed users (all about 0) still ranks sensibly instead of piling into one band.
        List<Row> rows = lifetime.stream()
                .map(r -> new Row(r.getUserId(), r.getTotalXp() != null ? r.getTotalXp() : 0.0,
                        scores.getOrDefault(r.getUserId(), 0.0)))
                .sorted(Comparator.comparingDouble(Row::score).reversed()
                        .thenComparing(Comparator.comparingDouble(Row::totalXp).reversed())
                        .thenComparing(Row::userId))
                .toList();

        Map<Long, UserRank> existing = userRankRepository.findAll().stream()
                .collect(Collectors.toMap(UserRank::getUserId, Function.identity(), (a, b) -> a));
        LocalDateTime baselineExpiry = now.minus(Duration.ofHours(rankingProperties.movementWindowHours()));

        int position = 1;
        double previousScore = Double.NaN;
        double previousTotal = Double.NaN;
        int previousPosition = 1;

        for (Row row : rows) {
            // Dense ranking: only users equal on BOTH keys share a position.
            int effectivePosition = (row.score() == previousScore && row.totalXp() == previousTotal)
                    ? previousPosition : position;

            double topFraction = (effectivePosition - 1) / (double) totalUsers;
            RankTier tier = RankTier.fromTopFraction(topFraction);
            int division = tier.divisionFor(topFraction);
            int overallLevel = overallLevelService.overallLevelFor(row.totalXp());

            // Movement baseline: carried forward while it is fresh, rolled to the pre-update state once it
            // is older than the window, and absent for a user ranked for the first time (trend NEW).
            UserRank before = existing.get(row.userId());
            RankTier baseTier = null;
            Integer baseDivision = null;
            Integer basePosition = null;
            LocalDateTime baseAt = null;
            if (before != null) {
                if (before.getPreviousAt() == null || before.getPreviousAt().isBefore(baselineExpiry)) {
                    baseTier = before.getTier();
                    baseDivision = before.getDivision();
                    basePosition = before.getPosition();
                    baseAt = now;
                } else {
                    baseTier = before.getPreviousTier();
                    baseDivision = before.getPreviousDivision();
                    basePosition = before.getPreviousPosition();
                    baseAt = before.getPreviousAt();
                }
            }

            userRankRepository.upsert(row.userId(), row.totalXp(), overallLevel, tier.name(),
                    topFraction, effectivePosition, totalUsers, row.score(), division,
                    baseTier == null ? null : baseTier.name(), baseDivision, basePosition, baseAt);

            previousScore = row.score();
            previousTotal = row.totalXp();
            previousPosition = effectivePosition;
            position++;
        }

        log.info("Rank recompute finished: {} users ranked", totalUsers);
        return totalUsers;
    }

    // Sum of every recent award, each aged by how long ago its session happened. Awards older than the
    // lookback are not read at all: they are worth under half a percent.
    private Map<Long, Double> recencyScores(LocalDateTime now) {
        double halfLife = rankingProperties.halfLifeDays();
        LocalDateTime cutoff = RankScore.lookbackCutoff(now, halfLife);
        Map<Long, Double> scores = new HashMap<>();
        accumulate(scores, logXpOutcomeRepository.findAwardsSince(cutoff), now, halfLife);
        accumulate(scores, manualXpAwardRepository.findAwardsSince(cutoff), now, halfLife);
        return scores;
    }

    private static void accumulate(Map<Long, Double> scores, List<TimedXpProjection> awards,
                                   LocalDateTime now, double halfLife) {
        for (TimedXpProjection award : awards) {
            if (award.getXp() == null || award.getOccurredAt() == null) {
                continue;
            }
            scores.merge(award.getUserId(),
                    RankScore.decayed(award.getXp(), award.getOccurredAt(), now, halfLife), Double::sum);
        }
    }
}
