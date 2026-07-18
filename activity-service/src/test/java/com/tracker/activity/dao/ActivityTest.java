package com.tracker.activity.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Activity.effectiveXpMultiplier (issue #10)")
class ActivityTest {

    @Test
    @DisplayName("a positive per-activity multiplier is honored as an override")
    void usesOverrideWhenPositive() {
        Activity activity = Activity.builder().category(Category.STUDY).xpMultiplier(2.0).build();
        assertEquals(2.0, activity.effectiveXpMultiplier());
    }

    @Test
    @DisplayName("a zero multiplier (the primitive default for an omitted field) falls back to the category base")
    void fallsBackToCategoryWhenZero() {
        Activity activity = Activity.builder().category(Category.STUDY).xpMultiplier(0.0).build();
        assertEquals(1.5, activity.effectiveXpMultiplier()); // STUDY base
    }

    @Test
    @DisplayName("a negative multiplier falls back to the category base instead of producing negative XP")
    void fallsBackToCategoryWhenNegative() {
        Activity activity = Activity.builder().category(Category.GAMING).xpMultiplier(-3.0).build();
        assertEquals(0.5, activity.effectiveXpMultiplier()); // GAMING base
    }

    @Test
    @DisplayName("a null category with no override defaults to OTHER (1.0)")
    void nullCategoryDefaultsToOther() {
        Activity activity = Activity.builder().category(null).xpMultiplier(0.0).build();
        assertEquals(1.0, activity.effectiveXpMultiplier()); // OTHER base
    }
}
