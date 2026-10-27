package com.tracker.activity.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Map;

/**
 * Per-feature request options layered over the model defaults from {@code spring.ai.*}. The model name
 * and base URL stay in YAML; this adds only what YAML cannot express correctly.
 *
 * <p>The thinking switch has to be a JSON boolean. Binding it from a property would hand the server the
 * string {@code "false"}, which Docker Model Runner rejects with a 400, so it is built here in code.
 *
 * <p>For an OpenAI-compatible model the options are a COPY of the model's own defaults with the extras
 * added. Starting from a fresh {@code OpenAiChatOptions.builder()} instead would silently send Spring
 * AI's built-in default model name (gpt-4o-mini) in place of the configured one, because runtime
 * options win over the defaults.
 */
public final class AiRequestOptions {

    private AiRequestOptions() {
    }

    /**
     * @param temperature     overrides the configured temperature for this feature; {@code null} keeps it
     * @param disableThinking see {@link AiBackendProperties#disableThinking()}; only applies to an
     *                        OpenAI-compatible model
     * @return the options to use as the ChatClient default, or {@code null} when nothing is overridden
     */
    public static ChatOptions forModel(ChatModel model, Double temperature, boolean disableThinking) {
        if (disableThinking && model instanceof OpenAiChatModel openAi) {
            OpenAiChatOptions options = defaultsOf(openAi);
            options.setExtraBody(Map.of("chat_template_kwargs", Map.of("enable_thinking", false)));
            if (temperature != null) {
                options.setTemperature(temperature);
            }
            return options;
        }
        if (temperature != null) {
            return ChatOptions.builder().temperature(temperature).build();
        }
        return null;
    }

    private static OpenAiChatOptions defaultsOf(OpenAiChatModel model) {
        return model.getDefaultOptions() instanceof OpenAiChatOptions defaults
                ? defaults.copy()
                : new OpenAiChatOptions();
    }
}
