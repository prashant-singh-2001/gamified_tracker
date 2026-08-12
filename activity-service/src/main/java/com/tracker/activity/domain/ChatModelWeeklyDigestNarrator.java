package com.tracker.activity.domain;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Optional;

/**
 * Narrates a weekly digest via any Spring AI {@link ChatModel} (issue #65). Ollama and an
 * OpenAI-compatible local server (Docker Model Runner, vLLM, LocalAI, ...) differ only in which
 * Spring AI autoconfiguration supplies that bean -- both are {@link ChatModel}s, so this one
 * implementation covers both; switching backend is pure configuration
 * ({@code spring.ai.model.chat}), never a code change.
 */
public class ChatModelWeeklyDigestNarrator implements WeeklyDigestNarrator {

    private final ChatClient chatClient;
    private final WeeklyDigestPromptBuilder promptBuilder;

    public ChatModelWeeklyDigestNarrator(ChatModel chatModel, WeeklyDigestPromptBuilder promptBuilder) {
        this.chatClient = ChatClient.create(chatModel);
        this.promptBuilder = promptBuilder;
    }

    @Override
    public Optional<String> narrate(DigestFacts facts) {
        String response = chatClient.prompt()
                .system(promptBuilder.systemPrompt())
                .user(promptBuilder.userPrompt(facts))
                .call()
                .content();
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(promptBuilder.truncateNarrative(response));
    }
}
