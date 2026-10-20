package com.tracker.gamification.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The recency weighting behind a rank. Each award counts for {@code xp * 0.5^(age / halfLife)}: an
 * award made one half-life ago is worth half, two half-lives ago a quarter, and so on. A player who
 * keeps logging holds a high score, one who stops sinks, and nobody is ranked on a lifetime total
 * they stopped earning to.
 *
 * <p>Pure functions of their arguments (the caller passes {@code now}), so they are exact to test.
 */
public final class RankScore {

    /** Awards older than this many half-lives are ignored: they are worth under 0.4% and not worth reading. */
    public static final int LOOKBACK_HALF_LIVES = 8;

    private static final double MILLIS_PER_DAY = 24.0 * 60 * 60 * 1000;

    private RankScore() {
    }

    /** The decayed value of {@code xp} earned at {@code occurredAt}, seen from {@code now}. */
    public static double decayed(double xp, LocalDateTime occurredAt, LocalDateTime now, double halfLifeDays) {
        // A time in the future (clock skew) counts as "just now", never as more than its face value.
        double ageDays = Math.max(0.0, Duration.between(occurredAt, now).toMillis() / MILLIS_PER_DAY);
        return xp * Math.pow(0.5, ageDays / halfLifeDays);
    }

    /** The oldest award time still worth reading from the database. */
    public static LocalDateTime lookbackCutoff(LocalDateTime now, double halfLifeDays) {
        long millis = (long) (LOOKBACK_HALF_LIVES * halfLifeDays * MILLIS_PER_DAY);
        return now.minus(Duration.ofMillis(millis));
    }
}
