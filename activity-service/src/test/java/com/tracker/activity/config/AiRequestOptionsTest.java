package com.tracker.activity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AiRequestOptions")
class AiRequestOptionsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("an OpenAI-compatible model gets enable_thinking=false as a JSON BOOLEAN, which Model Runner requires")
    void thinkingFlagIsAJsonBoolean() throws Exception {
        ChatOptions options = AiRequestOptions.forModel(mock(OpenAiChatModel.class), null, true);

        OpenAiChatOptions openAi = assertInstanceOf(OpenAiChatOptions.class, options);
        // A string "false" is rejected by the server with a 400 ("expected boolean, got string"), so pin
        // the wire shape, not just the Java map.
        assertEquals("{\"chat_template_kwargs\":{\"enable_thinking\":false}}",
                mapper.writeValueAsString(openAi.getExtraBody()));
    }

    @Test
    @DisplayName("the configured model, and its other defaults, survive: only the extras are added")
    void configuredModelIsPreserved() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.getDefaultOptions()).thenReturn(
                OpenAiChatOptions.builder().model("ai/granite4.2").temperature(0.4).maxTokens(300).build());

        OpenAiChatOptions options = (OpenAiChatOptions) AiRequestOptions.forModel(model, null, true);

        // Regression: starting from a fresh builder sent Spring AI's own default model name (gpt-4o-mini),
        // and the server answered 404 "model not found".
        assertEquals("ai/granite4.2", options.getModel());
        assertEquals(0.4, options.getTemperature());
        assertEquals(300, options.getMaxTokens());
        assertNotNull(options.getExtraBody());
    }

    @Test
    @DisplayName("a feature temperature replaces the configured one but keeps the model")
    void temperatureOverridesButKeepsTheModel() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("ai/granite4.2").temperature(0.4).build());

        OpenAiChatOptions options = (OpenAiChatOptions) AiRequestOptions.forModel(model, 0.1, true);

        assertEquals("ai/granite4.2", options.getModel());
        assertEquals(0.1, options.getTemperature());
    }

    @Test
    @DisplayName("the feature temperature rides along with the thinking switch")
    void temperatureIsCarried() {
        OpenAiChatOptions options = (OpenAiChatOptions) AiRequestOptions.forModel(mock(OpenAiChatModel.class), 0.1, true);

        assertEquals(0.1, options.getTemperature());
        assertNotNull(options.getExtraBody());
    }

    @Test
    @DisplayName("with the switch off, an OpenAI model gets only the temperature override, no extra body")
    void switchOffSendsNoExtraBody() {
        ChatOptions options = AiRequestOptions.forModel(mock(OpenAiChatModel.class), 0.1, false);

        assertEquals(0.1, options.getTemperature());
        assertNull(options instanceof OpenAiChatOptions o ? o.getExtraBody() : null);
    }

    @Test
    @DisplayName("nothing to override means no options at all, so the model defaults apply untouched")
    void nothingToOverrideIsNull() {
        assertNull(AiRequestOptions.forModel(mock(OpenAiChatModel.class), null, false));
        assertNull(AiRequestOptions.forModel(mock(OllamaChatModel.class), null, true));
    }

    @Test
    @DisplayName("a non-OpenAI backend never receives the OpenAI-only field, only a temperature if asked")
    void ollamaGetsOnlyTheTemperature() {
        ChatModel ollama = mock(OllamaChatModel.class);

        ChatOptions options = AiRequestOptions.forModel(ollama, 0.2, true);

        assertEquals(0.2, options.getTemperature());
        assertEquals(false, options instanceof OpenAiChatOptions);
    }
}
