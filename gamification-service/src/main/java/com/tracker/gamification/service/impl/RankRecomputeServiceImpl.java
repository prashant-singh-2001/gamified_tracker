package com.tracker.gamification.service.impl;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dto.UserXpProjection;
import com.tracker.gamification.repository.LevelTrackerRepository;
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

import java.util.List;

@Service
@AllArgsConstructor
public class RankRecomputeServiceImpl implements RankRecomputeService {

    private static final Logger log = LoggerFactory.getLogger(RankRecomputeServiceImpl.class);

    private final LevelTrackerRepository levelTrackerRepository;
    private final UserRankRepository userRankRepository;
    private final OverallLevelService overallLevelService;

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${ranking.recompute-interval-ms:300000}")
    // Issue #82: same class of race as OutboxRelay -- with N instances every one would rebuild
    // the leaderboard on its own 5-minute tick. lockAtMostFor stays under the recompute interval
    // so a crashed instance's lock never blocks the next legitimate tick. Since @SchedulerLock is
    // a Spring AOP interceptor on this bean method, it also guards the on-demand
    // POST /ranks/recompute path (LevelTrackerController -> this same method) -- acceptable, that
    // endpoint is rare and admin-triggered, and serializing it with the scheduled run is correct.
    @SchedulerLock(name = "rankRecompute_recompute", lockAtMostFor = "PT4M", lockAtLeastFor = "PT5S")
    public Integer recompute() {
        List<UserXpProjection> ranking = levelTrackerRepository.findAllUserTotals();
        int totalUsers = ranking.size();

        if (totalUsers == 0) {
            log.debug("Rank recompute skipped: no tracked users yet");
            return 0;
        }

        int position = 1;
        double previousXp = Double.NaN;
        int previousPosition = 1;

        for (UserXpProjection row : ranking) {
            double totalXp = row.getTotalXp() != null ? row.getTotalXp() : 0.0;

             int effectivePosition = (totalXp == previousXp) ? previousPosition : position;

            double topFraction = (effectivePosition - 1) / (double) totalUsers;
            RankTier tier = RankTier.fromTopFraction(topFraction);
            int overallLevel = overallLevelService.overallLevelFor(totalXp);

            userRankRepository.upsert(row.getUserId(), totalXp, overallLevel, tier.name(),
                    topFraction, effectivePosition, totalUsers);

            previousXp = totalXp;
            previousPosition = effectivePosition;
            position++;
        }

        log.info("Rank recompute finished: {} users ranked", totalUsers);
        return totalUsers;
    }
}
