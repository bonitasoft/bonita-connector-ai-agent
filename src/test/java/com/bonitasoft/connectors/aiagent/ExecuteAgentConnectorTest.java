package com.bonitasoft.connectors.aiagent;

import static org.assertj.core.api.Assertions.*;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class ExecuteAgentConnectorTest {

    private ExecuteAgentConnector connector;

    @BeforeEach
    void setUp() {
        connector = new ExecuteAgentConnector();
    }

    @Test
    void shouldFailValidationWhenLlmProviderMissing() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("llmApiKey", "test-key");
        inputs.put("llmModel", "gpt-4o");
        inputs.put("systemPrompt", "You are a helpful assistant");
        inputs.put("userPrompt", "Hello");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("llmProvider is mandatory");
    }

    @Test
    void shouldFailValidationWhenApiKeyMissing() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("llmProvider", "openai");
        inputs.put("llmModel", "gpt-4o");
        inputs.put("systemPrompt", "You are a helpful assistant");
        inputs.put("userPrompt", "Hello");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("llmApiKey is mandatory");
    }

    @Test
    void shouldFailValidationWhenModelMissing() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("llmProvider", "openai");
        inputs.put("llmApiKey", "test-key");
        inputs.put("systemPrompt", "You are a helpful assistant");
        inputs.put("userPrompt", "Hello");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("llmModel is mandatory");
    }

    @Test
    void shouldFailValidationWhenSystemPromptMissing() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("llmProvider", "openai");
        inputs.put("llmApiKey", "test-key");
        inputs.put("llmModel", "gpt-4o");
        inputs.put("userPrompt", "Hello");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("systemPrompt is mandatory");
    }

    @Test
    void shouldFailValidationWhenUserPromptMissing() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("llmProvider", "openai");
        inputs.put("llmApiKey", "test-key");
        inputs.put("llmModel", "gpt-4o");
        inputs.put("systemPrompt", "You are a helpful assistant");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("userPrompt is mandatory");
    }

    @Test
    void shouldPassValidationWithAllMandatoryInputs() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("llmProvider", "openai");
        inputs.put("llmApiKey", "test-key");
        inputs.put("llmModel", "gpt-4o");
        inputs.put("systemPrompt", "You are a helpful assistant");
        inputs.put("userPrompt", "Hello");
        connector.setInputParameters(inputs);

        // Should not throw
        connector.validateInputParameters();
    }

    @Test
    void shouldApplyDefaultValues() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("llmProvider", "openai");
        inputs.put("llmApiKey", "test-key");
        inputs.put("llmModel", "gpt-4o");
        inputs.put("systemPrompt", "You are a helpful assistant");
        inputs.put("userPrompt", "Hello");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        // Access configuration via reflection to verify defaults
        var configField = AbstractAiAgentConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        AiAgentConfiguration config = (AiAgentConfiguration) configField.get(connector);

        assertThat(config.getMaxIterations()).isEqualTo(10);
        assertThat(config.getMaxTokenBudget()).isEqualTo(100000);
        assertThat(config.getTemperature()).isEqualTo(0.1);
        assertThat(config.getConnectTimeout()).isEqualTo(30000);
        assertThat(config.getReadTimeout()).isEqualTo(120000);
        assertThat(config.getToolApprovalMode()).isEqualTo("none");
    }
}
