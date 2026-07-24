package com.tracker.gamification.dao;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "overall_level_threshold")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OverallLevelThreshold {

    @Id
    private Integer level;

    private double threshold;

}
