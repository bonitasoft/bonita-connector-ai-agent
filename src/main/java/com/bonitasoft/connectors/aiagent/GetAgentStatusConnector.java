package com.bonitasoft.connectors.aiagent;

import lombok.extern.slf4j.Slf4j;

/**
 * Retrieves the current status of an AI Agent execution.
 * Does not require LLM connection - reads from in-memory execution store.
 */
@Slf4j
public class GetAgentStatusConnector extends AbstractAiAgentConnector {

    // === Input parameter name constants ===
    static final String INPUT_EXECUTION_ID = "executionId";

    // === Output parameter name constants ===
    static final String OUTPUT_EXECUTION_ID = "executionId";
    static final String OUTPUT_STATUS = "status";
    static final String OUTPUT_CURRENT_ITERATION = "currentIteration";
    static final String OUTPUT_TOKENS_USED = "tokensUsed";
    static final String OUTPUT_TOOL_CALL_COUNT = "toolCallCount";
    static final String OUTPUT_ELAPSED_TIME_MS = "elapsedTimeMs";
    static final String OUTPUT_AGENT_RESULT = "agentResult";
    static final String OUTPUT_PENDING_TOOL_CALL = "pendingToolCall";

    @Override
    protected AiAgentConfiguration buildConfiguration() {
        return AiAgentConfiguration.builder()
                .llmProvider("none")
                .llmApiKey("none")
                .llmModel("none")
                .executionId(readStringInput(INPUT_EXECUTION_ID))
                .build();
    }

    @Override
    protected void validateConfiguration(AiAgentConfiguration config) {
        if (config.getExecutionId() == null || config.getExecutionId().isBlank()) {
            throw new IllegalArgumentException("executionId is mandatory");
        }
    }

    @Override
    protected void doExecute() throws AiAgentException {
        log.info("Executing GetAgentStatus connector for executionId={}", configuration.getExecutionId());

        AiAgentClient.StatusResult result = client.getAgentStatus(configuration.getExecutionId());

        setOutputParameter(OUTPUT_EXECUTION_ID, result.executionId());
        setOutputParameter(OUTPUT_STATUS, result.status());
        setOutputParameter(OUTPUT_CURRENT_ITERATION, result.currentIteration());
        setOutputParameter(OUTPUT_TOKENS_USED, result.tokensUsed());
        setOutputParameter(OUTPUT_TOOL_CALL_COUNT, result.toolCallCount());
        setOutputParameter(OUTPUT_ELAPSED_TIME_MS, result.elapsedTimeMs());
        setOutputParameter(OUTPUT_AGENT_RESULT, result.agentResult());
        setOutputParameter(OUTPUT_PENDING_TOOL_CALL, result.pendingToolCall());

        log.info("GetAgentStatus connector completed, status={}", result.status());
    }
}
