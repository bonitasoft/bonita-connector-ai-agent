package com.bonitasoft.connectors.aiagent;

import lombok.extern.slf4j.Slf4j;

/**
 * Resumes a paused AI Agent execution after human-in-the-loop tool approval.
 */
@Slf4j
public class ResumeAgentConnector extends AbstractAiAgentConnector {

    // === Input parameter name constants ===
    static final String INPUT_LLM_PROVIDER = "llmProvider";
    static final String INPUT_LLM_API_KEY = "llmApiKey";
    static final String INPUT_LLM_MODEL = "llmModel";
    static final String INPUT_LLM_BASE_URL = "llmBaseUrl";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_EXECUTION_ID = "executionId";
    static final String INPUT_APPROVAL_DECISION = "approvalDecision";
    static final String INPUT_MODIFIED_TOOL_PARAMS = "modifiedToolParams";

    // === Output parameter name constants ===
    static final String OUTPUT_EXECUTION_ID = "executionId";
    static final String OUTPUT_STATUS = "status";
    static final String OUTPUT_AGENT_RESULT = "agentResult";
    static final String OUTPUT_AGENT_RESULT_JSON = "agentResultJson";
    static final String OUTPUT_ITERATIONS = "iterations";
    static final String OUTPUT_TOKENS_USED = "tokensUsed";
    static final String OUTPUT_TOOL_CALL_COUNT = "toolCallCount";
    static final String OUTPUT_PENDING_TOOL_CALL = "pendingToolCall";

    @Override
    protected AiAgentConfiguration buildConfiguration() {
        return AiAgentConfiguration.builder()
                .llmProvider(readStringInput(INPUT_LLM_PROVIDER))
                .llmApiKey(readStringInput(INPUT_LLM_API_KEY))
                .llmModel(readStringInput(INPUT_LLM_MODEL))
                .llmBaseUrl(readStringInput(INPUT_LLM_BASE_URL))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 120000))
                .executionId(readStringInput(INPUT_EXECUTION_ID))
                .approvalDecision(readStringInput(INPUT_APPROVAL_DECISION))
                .modifiedToolParams(readStringInput(INPUT_MODIFIED_TOOL_PARAMS))
                .build();
    }

    @Override
    protected void validateConfiguration(AiAgentConfiguration config) {
        validateLlmConfiguration(config);
        if (config.getExecutionId() == null || config.getExecutionId().isBlank()) {
            throw new IllegalArgumentException("executionId is mandatory");
        }
        if (config.getApprovalDecision() == null || config.getApprovalDecision().isBlank()) {
            throw new IllegalArgumentException("approvalDecision is mandatory (approve, reject, or abort)");
        }
    }

    @Override
    protected void doExecute() throws AiAgentException {
        log.info("Executing ResumeAgent connector for executionId={}, decision={}",
                configuration.getExecutionId(), configuration.getApprovalDecision());

        AiAgentClient.ExecuteResult result = client.resumeAgent(
                configuration.getExecutionId(),
                configuration.getApprovalDecision(),
                configuration.getModifiedToolParams()
        );

        setOutputParameter(OUTPUT_EXECUTION_ID, result.executionId());
        setOutputParameter(OUTPUT_STATUS, result.status());
        setOutputParameter(OUTPUT_AGENT_RESULT, result.agentResult());
        setOutputParameter(OUTPUT_AGENT_RESULT_JSON, result.agentResultJson());
        setOutputParameter(OUTPUT_ITERATIONS, result.iterations());
        setOutputParameter(OUTPUT_TOKENS_USED, result.tokensUsed());
        setOutputParameter(OUTPUT_TOOL_CALL_COUNT, result.toolCallCount());
        setOutputParameter(OUTPUT_PENDING_TOOL_CALL, result.pendingToolCall());

        log.info("ResumeAgent connector completed with status={}", result.status());
    }
}
