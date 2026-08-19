package com.tracker.activity.domain;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Parses natural-language activity descriptions via any Spring AI {@link ChatModel} (issue #70).
 * Same one-implementation-covers-both-backends shape as {@link ChatModelWeeklyDigestNarrator}
 * (#65): Ollama and an OpenAI-compatible local server differ only in which autoconfiguration
 * supplies the {@link ChatModel} bean.
 *
 * <p>Structured output goes through Spring AI's {@code call().entity(Class)}. Confirmed by
 * decompiling {@code spring-ai-client-chat-1.1.8.jar}'s {@code ChatModelCallAdvisor} rather than
 * assumed: {@code entity(Class)} builds a {@code BeanOutputConverter} for the target type and the
 * advisor chain automatically appends its format instructions to the outgoing prompt, so no manual
 * {@code {format}} placeholder is needed in {@link NaturalLogPromptBuilder}. A reply that fails to
 * parse into {@link ParsedLogIntent} throws out of {@code entity(...)} -- caught by the caller, the
 * same degrade-don't-500 contract {@link NaturalLanguageLogParser} documents.
 */
public class ChatModelNaturalLanguageLogParser implements NaturalLanguageLogParser {

    private final ChatClient chatClient;
    private final NaturalLogPromptBuilder promptBuilder;
    private final Clock clock;

    public ChatModelNaturalLanguageLogParser(ChatModel chatModel, NaturalLogPromptBuilder promptBuilder, Clock clock) {
        this.chatClient = ChatClient.create(chatModel);
        this.promptBuilder = promptBuilder;
        this.clock = clock;
    }

    @Override
    public Optional<ParsedLogIntent> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        var today = LocalDate.now(clock).getDayOfWeek();
        ParsedLogIntent intent = chatClient.prompt()
                .system(promptBuilder.systemPrompt(today))
                .user(promptBuilder.userPrompt(text))
                .call()
                .entity(ParsedLogIntent.class);
        return Optional.ofNullable(intent);
    }
}
