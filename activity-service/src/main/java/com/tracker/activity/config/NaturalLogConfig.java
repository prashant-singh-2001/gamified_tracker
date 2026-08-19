package com.tracker.activity.config;

import com.tracker.activity.domain.ChatModelNaturalLanguageLogParser;
import com.tracker.activity.domain.LogIntentResolver;
import com.tracker.activity.domain.NaturalLanguageLogParser;
import com.tracker.activity.domain.NaturalLogPromptBuilder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties(NaturalLogProperties.class)
public class NaturalLogConfig {

    // First feature in this module needing a Clock. A bean (rather than calling
    // Clock.systemDefaultZone() directly wherever needed) is what lets a fixed-clock test swap it
    // out via context configuration if that's ever needed beyond LogIntentResolverTest's direct
    // constructor use, and gives the next Clock-dependent feature one place to reuse.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public LogIntentResolver logIntentResolver(Clock clock, SessionIntegrityProperties sessionIntegrityProperties) {
        // Reuses session-integrity's existing cap (#67) rather than a second, natural-log-specific
        // one -- one duration ceiling for the whole service, not two that could quietly disagree.
        return new LogIntentResolver(clock, sessionIntegrityProperties.maxDurationMinutes());
    }

    @Bean
    public NaturalLogPromptBuilder naturalLogPromptBuilder(NaturalLogProperties properties) {
        return new NaturalLogPromptBuilder(properties.maxInputChars());
    }

    // ObjectProvider + fail-open, same shape as InsightsConfig (#65): getIfAvailable() degrades to
    // the no-op below rather than failing context startup when natural-log.enabled=true but
    // spring.ai.model.chat=none (flag on, no backend chosen).
    @Bean
    @ConditionalOnProperty(prefix = "natural-log", name = "enabled", havingValue = "true")
    public NaturalLanguageLogParser chatModelNaturalLanguageLogParser(
            ObjectProvider<ChatModel> chatModel, NaturalLogPromptBuilder promptBuilder, Clock clock) {
        ChatModel model = chatModel.getIfAvailable();
        return model == null
                ? text -> Optional.empty()
                : new ChatModelNaturalLanguageLogParser(model, promptBuilder, clock);
    }

    // Declared second on purpose. With natural-log.enabled=false (the default, and CI) nothing
    // AI-related is constructed and the endpoint degrades to "couldn't parse that, try again."
    @Bean
    @ConditionalOnMissingBean(NaturalLanguageLogParser.class)
    public NaturalLanguageLogParser disabledNaturalLanguageLogParser() {
        return text -> Optional.empty();
    }
}
