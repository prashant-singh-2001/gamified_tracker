package com.tracker.gamification.dao;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table
@Data
@NoArgsConstructor
public class UserRank implements Persistable<Long> {

    double totalXp;
    int overallLevel;
    @Enumerated(EnumType.STRING)
    RankTier tier;
    double percentile;
    int position;
    int totalUsers;
    LocalDateTime updatedAt;
    @Id
    private Long userId;

    // Persistable<Long>: userId is an application-assigned natural key (the real platform user
    // id), not a DB-generated surrogate. Without this, Spring Data's default null-id "isNew"
    // heuristic treats any entity with a non-null id as already existing and issues an UPDATE,
    // which matches zero rows for a genuinely new row. True until persisted or loaded.
    @Transient
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private boolean isNew = true;

    public UserRank(double totalXp, int overallLevel, RankTier tier, double percentile,
                     int position, int totalUsers, LocalDateTime updatedAt, Long userId) {
        this.totalXp = totalXp;
        this.overallLevel = overallLevel;
        this.tier = tier;
        this.percentile = percentile;
        this.position = position;
        this.totalUsers = totalUsers;
        this.updatedAt = updatedAt;
        this.userId = userId;
    }

    @Override
    public Long getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }
}
