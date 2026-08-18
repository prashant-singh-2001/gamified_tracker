package com.tracker.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI weekly coaching digest thresholds (issue #65). {@code enabled} gates the whole feature; which
 * backend actually answers (Ollama vs. an OpenAI-compatible server such as Docker Model Runner) is
 * Spring AI's own {@code spring.ai.model.chat} selector, not a property here — two sources of truth
 * for the same decision would be able to disagree.
 */
@ConfigurationProperties(prefix = "insights")
public record InsightsProperties(
        boolean enabled,
        // Prompt-injection / cost bound. `notes` is TEXT in Postgres (V1__create_activity_schema.sql)
        // with no @Size on ActivityLogRequest and no @Column on the entity -- a user can store an
        // arbitrarily large note today, so this cap must be enforced in Java, never assumed from schema.
        int maxNotes,
        int maxNoteChars,
        int maxNarrativeChars) {
}
