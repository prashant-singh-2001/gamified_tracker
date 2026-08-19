package com.tracker.activity.domain;

/**
 * A coarse, LLM-friendly time-of-day bucket for natural-language activity logging (issue #70).
 * Exists so a model is never asked to emit a clock time for a vague phrase like "this morning" --
 * it only classifies into a bucket; {@link LogIntentResolver} is what turns that into an hour.
 */
public enum TimeOfDay {
    MORNING, AFTERNOON, EVENING, NIGHT;

    public int defaultHour() {
        return switch (this) {
            case MORNING -> 9;
            case AFTERNOON -> 14;
            case EVENING -> 19;
            case NIGHT -> 22;
        };
    }
}
