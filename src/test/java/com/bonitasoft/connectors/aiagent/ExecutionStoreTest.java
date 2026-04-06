package com.bonitasoft.connectors.aiagent;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionStoreTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        ExecutionStore.clear();
    }

    @Test
    void shouldStoreAndRetrieveState() {
        AiAgentConfiguration config = AiAgentConfiguration.builder()
                .llmProvider("openai").llmApiKey("key").llmModel("gpt-4o").build();
        ExecutionState state = new ExecutionState("exec-1", config);

        ExecutionStore.put("exec-1", state);

        assertThat(ExecutionStore.get("exec-1")).isSameAs(state);
        assertThat(ExecutionStore.contains("exec-1")).isTrue();
    }

    @Test
    void shouldReturnNullForMissingKey() {
        assertThat(ExecutionStore.get("nonexistent")).isNull();
        assertThat(ExecutionStore.contains("nonexistent")).isFalse();
    }

    @Test
    void shouldRemoveState() {
        AiAgentConfiguration config = AiAgentConfiguration.builder()
                .llmProvider("openai").llmApiKey("key").llmModel("gpt-4o").build();
        ExecutionState state = new ExecutionState("exec-2", config);
        ExecutionStore.put("exec-2", state);

        ExecutionState removed = ExecutionStore.remove("exec-2");

        assertThat(removed).isSameAs(state);
        assertThat(ExecutionStore.contains("exec-2")).isFalse();
    }
}
