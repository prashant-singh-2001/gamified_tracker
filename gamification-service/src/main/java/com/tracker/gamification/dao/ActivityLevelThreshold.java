package com.tracker.gamification.dao;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "activity_level_threshold")
@NoArgsConstructor
@Data
@AllArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
@Builder
public class ActivityLevelThreshold {
    @EmbeddedId
    private ActivityLevelThresholdId id;

    private double xpRequired;
}
