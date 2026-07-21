package com.tracker.gamification.repository;

import com.tracker.gamification.dao.UserAchievement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// grantIfAbsent's native "ON CONFLICT ... DO NOTHING" query can't be exercised here: H2 2.3.232
// doesn't parse the ON CONFLICT clause at all (confirmed empirically, in both H2's regular mode and
// MODE=PostgreSQL compatibility mode — both fail with an identical syntax error). This is a
// pre-existing gap shared by LevelTracker.insertIfAbsent, which uses the same idiom and has never
// been exercised against a real database either. What IS verified here against H2: the
// uk_user_achievement unique constraint is actually enforced at the DB level, and the derived
// query methods behave correctly.
@DataJpaTest
public class UserAchievementRepositoryTest {

    @Autowired
    private UserAchievementRepository userAchievementRepository;

    private static UserAchievement award(Long userId, Long achievementId, LocalDateTime unlockedAt) {
        return new UserAchievement(null, userId, achievementId, unlockedAt);
    }

    @Test
    void uniqueConstraint_rejectsASecondRowForTheSamePair() {
        // Arrange
        userAchievementRepository.saveAndFlush(award(1L, 10L, LocalDateTime.now()));

        // Act & Assert — uk_user_achievement (user_id, achievement_id) blocks the duplicate
        assertThrows(DataIntegrityViolationException.class, () ->
                userAchievementRepository.saveAndFlush(award(1L, 10L, LocalDateTime.now())));
    }

    @Test
    void findByUserIdOrderByUnlockedAtDesc_returnsOnlyThatUsersAwards_newestFirst() {
        // Arrange
        LocalDateTime earlier = LocalDateTime.now().minusDays(1);
        LocalDateTime later = LocalDateTime.now();

        userAchievementRepository.save(award(2L, 20L, earlier));
        userAchievementRepository.save(award(2L, 21L, later));
        userAchievementRepository.save(award(99L, 20L, later));

        // Act
        List<UserAchievement> results = userAchievementRepository.findByUserIdOrderByUnlockedAtDesc(2L);

        // Assert
        assertEquals(2, results.size());
        assertEquals(21L, results.get(0).getAchievementId());
        assertEquals(20L, results.get(1).getAchievementId());
    }

    @Test
    void existsByUserIdAndAchievementId_reflectsGrantedState() {
        // Arrange
        userAchievementRepository.save(award(3L, 30L, LocalDateTime.now()));

        // Act & Assert
        assertTrue(userAchievementRepository.existsByUserIdAndAchievementId(3L, 30L));
        assertFalse(userAchievementRepository.existsByUserIdAndAchievementId(3L, 99L));
    }
}
