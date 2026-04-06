package com.bonitasoft.connectors.aiagent;

import lombok.extern.slf4j.Slf4j;

/**
 * Starts a new AI Agent execution with the ReAct loop.
 * Supports multi-provider LLM (OpenAI, Anthropic, Google) and human-in-the-loop tool approval.
 */
@Slf4j
public class ExecuteAgentConnector extends AbstractAiAgentConnector {

    // === Input parameter name constants ===
    static final String INPUT_LLM_PROVIDER = "llmProvider";
    static final String INPUT_LLM_API_KEY = "llmApiKey";
    static final String INPUT_LLM_MODEL = "llmModel";
    static final String INPUT_LLM_BASE_URL = "llmBaseUrl";
    static final String INPUT_MAX_ITERATIONS = "maxIterations";
    static final String INPUT_MAX_TOKEN_BUDGET = "maxTokenBudget";
    static final String INPUT_TEMPERATURE = "temperature";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_SYSTEM_PROMPT = "systemPrompt";
    static final String INPUT_USER_PROMPT = "userPrompt";
    static final String INPUT_TOOLS_JSON = "toolsJson";
    static final String INPUT_CONTEXT_JSON = "contextJson";
    static final String INPUT_TOOL_APPROVAL_MODE = "toolApprovalMode";

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
                .maxIterations(readIntegerInput(INPUT_MAX_ITERATIONS, 10))
                .maxTokenBudget(readIntegerInput(INPUT_MAX_TOKEN_BUDGET, 100000))
                .temperature(readDoubleInput(INPUT_TEMPERATURE, 0.1))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 120000))
                .systemPrompt(readStringInput(INPUT_SYSTEM_PROMPT))
                .userPrompt(readStringInput(INPUT_USER_PROMPT))
                .toolsJson(readStringInput(INPUT_TOOLS_JSON))
                .contextJson(readStringInput(INPUT_CONTEXT_JSON))
                .toolApprovalMode(readStringInput(INPUT_TOOL_APPROVAL_MODE, "none"))
                .build();
    }

    @Override
    protected void validateConfiguration(AiAgentConfiguration config) {
        validateLlmConfiguration(config);
        if (config.getSystemPrompt() == null || config.getSystemPrompt().isBlank()) {
            throw new IllegalArgumentException("systemPrompt is mandatory");
        }
        if (config.getUserPrompt() == null || config.getUserPrompt().isBlank()) {
            throw new IllegalArgumentException("userPrompt is mandatory");
        }
    }

    @Override
    protected void doExecute() throws AiAgentException {
        log.info("Executing ExecuteAgent connector with provider={}, model={}",
                configuration.getLlmProvider(), configuration.getLlmModel());

        AiAgentClient.ExecuteResult result = client.executeAgent(
                configuration.getSystemPrompt(),
                configuration.getUserPrompt(),
                configuration.getToolsJson(),
                configuration.getContextJson(),
                configuration.getToolApprovalMode()
        );

        setOutputParameter(OUTPUT_EXECUTION_ID, result.executionId());
        setOutputParameter(OUTPUT_STATUS, result.status());
        setOutputParameter(OUTPUT_AGENT_RESULT, result.agentResult());
        setOutputParameter(OUTPUT_AGENT_RESULT_JSON, result.agentResultJson());
        setOutputParameter(OUTPUT_ITERATIONS, result.iterations());
        setOutputParameter(OUTPUT_TOKENS_USED, result.tokensUsed());
        setOutputParameter(OUTPUT_TOOL_CALL_COUNT, result.toolCallCount());
        setOutputParameter(OUTPUT_PENDING_TOOL_CALL, result.pendingToolCall());

        log.info("ExecuteAgent connector completed with status={}, iterations={}",
                result.status(), result.iterations());
    }
}
