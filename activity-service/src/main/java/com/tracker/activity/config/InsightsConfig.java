package com.tracker.activity.config;

import com.tracker.activity.domain.ChatModelWeeklyDigestNarrator;
import com.tracker.activity.domain.WeeklyDigestNarrator;
import com.tracker.activity.domain.WeeklyDigestPromptBuilder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@EnableConfigurationProperties(InsightsProperties.class)
public class InsightsConfig {

    @Bean
    public WeeklyDigestPromptBuilder weeklyDigestPromptBuilder(InsightsProperties properties) {
        return new WeeklyDigestPromptBuilder(
                properties.maxNotes(), properties.maxNoteChars(), properties.maxNarrativeChars());
    }

    // ObjectProvider, not @ConditionalOnBean: the latter is only reliable inside auto-configuration
    // classes, and this is user config. getIfAvailable() also handles the legitimate combination
    // insights.enabled=true + spring.ai.model.chat=none (flag on, no backend chosen) by degrading to
    // the no-op below rather than failing context startup.
    @Bean
    @ConditionalOnProperty(prefix = "insights", name = "enabled", havingValue = "true")
    public WeeklyDigestNarrator chatModelWeeklyDigestNarrator(
            ObjectProvider<ChatModel> chatModel, WeeklyDigestPromptBuilder promptBuilder) {
        ChatModel model = chatModel.getIfAvailable();
        return model == null
                ? facts -> Optional.empty()
                : new ChatModelWeeklyDigestNarrator(model, promptBuilder);
    }

    // Declared second on purpose. With insights.enabled=false (the default, and CI) nothing
    // AI-related is constructed and the endpoint degrades to numbers-only.
    @Bean
    @ConditionalOnMissingBean(WeeklyDigestNarrator.class)
    public WeeklyDigestNarrator disabledWeeklyDigestNarrator() {
        return facts -> Optional.empty();
    }
}
