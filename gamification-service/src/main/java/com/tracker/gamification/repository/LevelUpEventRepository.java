package com.tracker.gamification.repository;

import com.tracker.gamification.dao.LevelUpEvent;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
