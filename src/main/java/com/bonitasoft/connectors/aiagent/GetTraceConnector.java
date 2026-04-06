package com.bonitasoft.connectors.aiagent;

import lombok.extern.slf4j.Slf4j;

/**
 * Retrieves the execution trace for an AI Agent execution.
 * Does not require LLM connection - reads from in-memory execution store.
 */
@Slf4j
public class GetTraceConnector extends AbstractAiAgentConnector {

    // === Input parameter name constants ===
    static final String INPUT_EXECUTION_ID = "executionId";
    static final String INPUT_INCLUDE_TOOL_RESPONSES = "includeToolResponses";

    // === Output parameter name constants ===
    static final String OUTPUT_EXECUTION_ID = "executionId";
    static final String OUTPUT_ENTRY_COUNT = "entryCount";
    static final String OUTPUT_TRACE_JSON = "traceJson";

    @Override
    protected AiAgentConfiguration buildConfiguration() {
        return AiAgentConfiguration.builder()
                .llmProvider("none")
                .llmApiKey("none")
                .llmModel("none")
                .executionId(readStringInput(INPUT_EXECUTION_ID))
                .includeToolResponses(readBooleanInput(INPUT_INCLUDE_TOOL_RESPONSES, false))
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
        log.info("Executing GetTrace connector for executionId={}", configuration.getExecutionId());

        AiAgentClient.TraceResult result = client.getTrace(
                configuration.getExecutionId(),
                configuration.isIncludeToolResponses()
        );

        setOutputParameter(OUTPUT_EXECUTION_ID, result.executionId());
        setOutputParameter(OUTPUT_ENTRY_COUNT, result.entryCount());
        setOutputParameter(OUTPUT_TRACE_JSON, result.traceJson());

        log.info("GetTrace connector completed with {} entries", result.entryCount());
    }
}
