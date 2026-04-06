package com.bonitasoft.connectors.aiagent;

import static org.assertj.core.api.Assertions.*;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class GetAgentStatusConnectorTest {

    private GetAgentStatusConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GetAgentStatusConnector();
        ExecutionStore.clear();
    }

    @AfterEach
    void tearDown() {
        ExecutionStore.clear();
    }

    @Test
    void shouldReturnStatusForExistingExecution() throws Exception {
        // Arrange: create an execution state
        AiAgentConfiguration config = AiAgentConfiguration.builder()
                .llmProvider("openai")
                .llmApiKey("test-key")
                .llmModel("gpt-4o")
                .build();
        ExecutionState state = new ExecutionState("test-exec-123", config);
        state.setStatus("running");
        state.setCurrentIteration(3);
        state.addTokensUsed(1500);
        ExecutionStore.put("test-exec-123", state);

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("executionId", "test-exec-123");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("executionId")).isEqualTo("test-exec-123");
        assertThat(outputs.get("status")).isEqualTo("running");
        assertThat(outputs.get("currentIteration")).isEqualTo(3);
        assertThat(outputs.get("tokensUsed")).isEqualTo(1500);
    }

    @Test
    void shouldFailWhenExecutionNotFound() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("executionId", "nonexistent-id");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("Execution not found");
    }

    @Test
    void shouldFailValidationWhenExecutionIdMissing() {
        Map<String, Object> inputs = new HashMap<>();
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("executionId is mandatory");
    }

    @Test
    void shouldReturnPausedStatusWithPendingToolCall() throws Exception {
        AiAgentConfiguration config = AiAgentConfiguration.builder()
                .llmProvider("openai")
                .llmApiKey("test-key")
                .llmModel("gpt-4o")
                .build();
        ExecutionState state = new ExecutionState("paused-exec", config);
        state.setStatus("paused");
        state.setPendingToolCall("[{\"id\":\"tc_1\",\"function\":{\"name\":\"search\"}}]");
        ExecutionStore.put("paused-exec", state);

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("executionId", "paused-exec");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("status")).isEqualTo("paused");
        assertThat(outputs.get("pendingToolCall")).isNotNull();
    }
}
