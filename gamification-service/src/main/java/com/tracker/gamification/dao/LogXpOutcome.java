package com.tracker.gamification.dao;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * What applying one activity log's XP did: how much, and the activity's level before and after.
 * Append-only, one row per log, written in the same transaction as the XP mutation.
 */
@Entity
@Table(name = "log_xp_outcome")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogXpOutcome implements Persistable<Long> {

    @Id
    private Long logId;
    private Long userId;
    private Long activityId;
    private double xpApplied;
    private int previousLevel;
    private int newLevel;
    private boolean leveledUp;
    private LocalDateTime appliedAt;

    // Same trap ProcessedEvent documents (#82): a manually-assigned @Id with no @GeneratedValue /
    // @Version makes Spring Data's default isNew() always false, so save() would compile to a
    // merge() (an extra SELECT, and a silent UPDATE instead of a failing INSERT on a duplicate).
    // The table is append-only, so every row is new.
    @Override
    @Transient
    public Long getId() {
        return logId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
