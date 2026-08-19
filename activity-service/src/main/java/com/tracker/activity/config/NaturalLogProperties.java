package com.tracker.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Natural-language activity logging thresholds (issue #70). {@code enabled} gates the whole
 * feature; which backend actually answers (Ollama vs. an OpenAI-compatible server such as Docker
 * Model Runner) is Spring AI's own {@code spring.ai.model.chat} selector -- shared with #65's
 * weekly digest, not a second property here.
 */
@ConfigurationProperties(prefix = "natural-log")
public record NaturalLogProperties(
        boolean enabled,
        // Cost/injection bound on the raw text a client can send -- the endpoint takes a plain
        // string body with no @Size of its own, so this is the only cap on it.
        int maxInputChars) {
}
