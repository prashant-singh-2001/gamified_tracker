package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LevelUpEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LevelUpEventRepository extends JpaRepository<LevelUpEvent, Long> {

    // #85: all four finders below are user_id-prefixed, which is what idx_level_up_event_user_created
    // (V6) serves. Dropping the userId param, or reordering it to trail in a future @Query, silently
    // falls back to a full table scan -- nothing fails, the endpoint just gets slower.
    List<LevelUpEvent> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<LevelUpEvent> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadFalse(Long userId);

    Optional<LevelUpEvent> findByIdAndUserId(Long id, Long userId);

    // POST /notifications/read-all: one UPDATE for the caller's unread rows rather than loading and
    // saving each. The userId predicate is what makes it the CALLER's rows only. Returns the number of
    // rows actually flipped, so a repeat call (nothing left unread) reports 0 -- idempotent. HQL names
    // the attribute (read), not the column (is_read); see LevelUpEvent for why they differ.
    @Modifying
    @Query("UPDATE LevelUpEvent e SET e.read = true WHERE e.userId = :userId AND e.read = false")
    int markAllReadForUser(@Param("userId") Long userId);
}
