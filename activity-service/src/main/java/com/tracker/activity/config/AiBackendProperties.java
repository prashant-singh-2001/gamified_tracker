package com.tracker.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings shared by every AI-backed feature (the weekly digest and natural-language logging) that
 * concern the model server rather than a feature.
 *
 * @param disableThinking send {@code chat_template_kwargs.enable_thinking=false} with every request to
 *                        an OpenAI-compatible server (Docker Model Runner, vLLM, LocalAI). Reasoning models
 *                        such as Granite 4.2 otherwise spend their whole token budget on hidden reasoning
 *                        and answer with EMPTY content, which breaks the JSON parse and blows the read
 *                        timeout. Models whose chat template has no such switch ignore the flag. Turn it off
 *                        for a server that rejects unknown request fields.
 */
@ConfigurationProperties(prefix = "ai-backend")
public record AiBackendProperties(boolean disableThinking) {
}
