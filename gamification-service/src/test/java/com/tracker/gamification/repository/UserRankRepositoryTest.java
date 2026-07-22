package com.tracker.gamification.repository;

import com.tracker.gamification.dao.RankTier;
import com.tracker.gamification.dao.UserRank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// upsert's native "ON CONFLICT ... DO UPDATE" clause can't be exercised here: H2 2.3.232 doesn't
// parse the ON CONFLICT clause at all (see UserAchievementRepositoryTest for the same,
// already-documented limitation). This test covers the derived queries instead, seeding rows via
// the standard JPA save path.
@DataJpaTest
public class UserRankRepositoryTest {

    @Autowired
    private UserRankRepository userRankRepository;

    private static UserRank rank(Long userId, RankTier tier, double totalXp) {
        return new UserRank(totalXp, 1, tier, 0.1, 1, 10, LocalDateTime.now(), userId);
    }

    @Test
    void findByUserId_returnsTheUsersSnapshot() {
        userRankRepository.save(rank(1L, RankTier.SUMMIT, 5000.0));

        Optional<UserRank> found = userRankRepository.findByUserId(1L);

        assertTrue(found.isPresent());
        assertEquals(RankTier.SUMMIT, found.get().getTier());
    }

    @Test
    void findByTierOrderByTotalXpDesc_returnsOnlyThatTier_orderedByXp() {
        userRankRepository.save(rank(2L, RankTier.PEAK, 300.0));
        userRankRepository.save(rank(3L, RankTier.PEAK, 500.0));
        userRankRepository.save(rank(4L, RankTier.RIDGE, 999.0));

        List<UserRank> peakMembers =
                userRankRepository.findByTierOrderByTotalXpDesc(RankTier.PEAK, PageRequest.of(0, 10));

        assertEquals(2, peakMembers.size());
        assertEquals(3L, peakMembers.get(0).getUserId());
        assertEquals(2L, peakMembers.get(1).getUserId());
    }

    @Test
    void countByTier_countsOnlyThatTier() {
        userRankRepository.save(rank(5L, RankTier.BASECAMP, 10.0));
        userRankRepository.save(rank(6L, RankTier.BASECAMP, 20.0));
        userRankRepository.save(rank(7L, RankTier.SUMMIT, 999.0));

        assertEquals(2, userRankRepository.countByTier(RankTier.BASECAMP));
        assertEquals(1, userRankRepository.countByTier(RankTier.SUMMIT));
    }
}
