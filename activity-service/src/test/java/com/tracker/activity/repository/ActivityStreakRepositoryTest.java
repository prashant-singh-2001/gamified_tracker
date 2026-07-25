package com.tracker.activity.repository;

import com.tracker.activity.dao.ActivityStreak;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// @Autowired field injection, matching the repo's other @DataJpaTest classes. Constructor
// injection here needs @TestConstructor(autowireMode = ALL), which isn't configured.
@DataJpaTest
public class ActivityStreakRepositoryTest {
    @Autowired
    private ActivityStreakRepository repository;

    private static ActivityStreak streak(Long userId, Long activityId, int current) {
        return ActivityStreak.builder().userId(userId).activityId(activityId).currentStreak(current).longestStreak(current).lastActivityDate(LocalDate.now()).build();
    }

    @Test
    void findByUserIdAndActivityId_returnsTheRow() {
        repository.save(streak(1L, 10L, 3));

        Optional<ActivityStreak> found = repository.findByUserIdAndActivityId(1L, 10L);

        assertTrue(found.isPresent());
        assertEquals(3, found.get().getCurrentStreak());
    }

    @Test
    void uniqueConstraint_rejectsDuplicateUserActivityPair() {
        repository.saveAndFlush(streak(2L, 20L, 1));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(streak(2L, 20L, 1)));
    }

    @Test
    void findByUserId_returnsOnlyThatUsersStreaks() {
        repository.save(streak(3L, 30L, 5));
        repository.save(streak(3L, 31L, 2));
        repository.save(streak(99L, 30L, 9));

        assertEquals(2, repository.findByUserId(3L).size());
    }
}
