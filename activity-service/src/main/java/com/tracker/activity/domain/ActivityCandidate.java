package com.tracker.activity.domain;

/**
 * A catalog entry flattened to plain strings (issue #66), so no {@link ActivityNameScorer}
 * implementation — lexical or a future semantic one — ever needs to see the JPA {@code Activity}
 * entity.
 */
public record ActivityCandidate(String name, String description, String category, boolean active) {
}
