package com.tracker.activity.config;

import com.tracker.activity.domain.ActivityMatcher;
import com.tracker.activity.domain.ActivityNameScorer;
import com.tracker.activity.domain.LexicalActivityNameScorer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ActivityNameMatchingProperties.class)
public class ActivityNameMatchingConfig {

    // The only scorer today. @ConditionalOnMissingBean so a future embedding/LLM-backed provider
    // can replace it by simply defining its own ActivityNameScorer bean — no edit here, no caller
    // change. No provider-selector property yet: one implementation doesn't need a selector, and
    // this is the cheaper seam. Add one when a second provider lands.
    @Bean
    @ConditionalOnMissingBean(ActivityNameScorer.class)
    public ActivityNameScorer lexicalActivityNameScorer() {
        return new LexicalActivityNameScorer();
    }

    @Bean
    public ActivityMatcher activityMatcher(ActivityNameScorer scorer, ActivityNameMatchingProperties properties) {
        return new ActivityMatcher(scorer, properties.autoResolveThreshold(), properties.ambiguityMargin(),
                properties.suggestionThreshold(), properties.maxSuggestions());
    }
}
