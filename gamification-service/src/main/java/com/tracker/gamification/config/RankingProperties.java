package com.tracker.gamification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ranking settings ({@code ranking.*}). {@code recompute-interval-ms} and {@code lock-at-most-for}
 * are read directly by the scheduler annotations on {@code RankRecomputeServiceImpl}.
 *
 * @param halfLifeDays        XP loses half its weight every this many days (default 14)
 * @param movementWindowHours how long a movement baseline is kept before it is rolled forward, i.e.
 *                            the horizon of "up 3 places" (default 24)
 */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(double halfLifeDays, int movementWindowHours) {

    public RankingProperties {
        if (halfLifeDays <= 0) {
            halfLifeDays = 14.0;
        }
        if (movementWindowHours <= 0) {
            movementWindowHours = 24;
        }
    }
}
