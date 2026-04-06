package com.bonitasoft.connectors.aiagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI Agent Orchestrator client.
 * Implements the ReAct (Reason + Act) loop with multi-provider LLM support
 * and human-in-the-loop tool approval.
 */
@Slf4j
public class AiAgentClient {

    private final AiAgentConfiguration config;
    private final ProviderAdapter provider;
    private final RetryPolicy retryPolicy;
    private final ObjectMapper mapper;

    public AiAgentClient(AiAgentConfiguration config) throws AiAgentException {
        this.config = config;
        this.mapper = new ObjectMapper();
        this.retryPolicy = new RetryPolicy(3);

        // Non-LLM operations (get-status, define-tools, get-trace) use provider="none"
        if ("none".equalsIgnoreCase(config.getLlmProvider())) {
            this.provider = null;
        } else {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                    .build();
            this.provider = ProviderAdapter.create(config, httpClient, mapper);
        }
    }

    // --- Execute Agent ---

    /**
     * Starts a new agent execution with the ReAct loop.
     * Returns an ExecutionState that is either completed or paused (waiting for tool approval).
     */
    public ExecuteResult executeAgent(String systemPrompt, String userPrompt,
                                       String toolsJson, String contextJson,
                                       String toolApprovalMode) throws AiAgentException {
        String executionId = UUID.randomUUID().toString();
        ExecutionState state = new ExecutionState(executionId, config);

        // Add context as system-level info if present
        String fullSystemPrompt = systemPrompt;
        if (contextJson != null && !contextJson.isBlank()) {
            fullSystemPrompt += "\n\n<context>\n" + contextJson + "\n</context>";
        }

        state.getMessages().add(ChatMessage.user(userPrompt));
        state.getTrace().add(new TraceEntry(0, "start", "Agent execution started", userPrompt, 0));

        ExecutionStore.put(executionId, state);

        return runReActLoop(state, fullSystemPrompt, toolsJson, toolApprovalMode);
    }

    private ExecuteResult runReActLoop(ExecutionState state, String systemPrompt,
                                        String toolsJson, String toolApprovalMode) throws AiAgentException {
        int maxIter = config.getMaxIterations();

        while (state.getCurrentIteration() < maxIter && "running".equals(state.getStatus())) {
            state.setCurrentIteration(state.getCurrentIteration() + 1);
            int iter = state.getCurrentIteration();

            log.info("ReAct iteration {}/{} for execution {}", iter, maxIter, state.getExecutionId());

            // Call LLM
            LlmResponse llmResponse = retryPolicy.execute(() ->
                    provider.chat(systemPrompt, state.getMessages(), toolsJson,
                            config.getTemperature(), config.getMaxTokenBudget()));

            state.addTokensUsed(llmResponse.tokensUsed());
            state.getTrace().add(new TraceEntry(iter, "llm_response",
                    "LLM responded" + (llmResponse.hasToolCalls() ? " with tool calls" : ""),
                    llmResponse.content(), llmResponse.tokensUsed()));

            if (llmResponse.hasToolCalls()) {
                // Agent wants to use tools
                state.getMessages().add(ChatMessage.assistantWithToolCalls(llmResponse.content(), llmResponse.toolCalls()));
                state.incrementToolCallCount();

                // Check if tool approval is needed
                if ("always".equalsIgnoreCase(toolApprovalMode) || "dangerous".equalsIgnoreCase(toolApprovalMode)) {
                    // Pause execution for human approval
                    state.setStatus("paused");
                    state.setPendingToolCall(llmResponse.toolCalls());
                    state.getTrace().add(new TraceEntry(iter, "paused",
                            "Waiting for tool approval", llmResponse.toolCalls(), 0));

                    return new ExecuteResult(state.getExecutionId(), "paused",
                            llmResponse.content(), null, iter, state.getTotalTokensUsed(),
                            state.getToolCallCount(), llmResponse.toolCalls());
                }

                // Auto-approve: simulate tool execution with placeholder
                simulateToolExecution(state, llmResponse.toolCalls());

            } else {
                // Agent is done reasoning - final answer
                state.getMessages().add(ChatMessage.assistant(llmResponse.content()));
                state.setStatus("completed");
                state.setAgentResult(llmResponse.content());
                state.setAgentResultJson(tryParseAsJson(llmResponse.content()));

                state.getTrace().add(new TraceEntry(iter, "completed",
                        "Agent completed", llmResponse.content(), 0));

                return new ExecuteResult(state.getExecutionId(), "completed",
                        llmResponse.content(), state.getAgentResultJson(),
                        iter, state.getTotalTokensUsed(), state.getToolCallCount(), null);
            }
        }

        // Max iterations reached
        state.setStatus("completed");
        String lastContent = !state.getMessages().isEmpty()
                ? state.getMessages().get(state.getMessages().size() - 1).content()
                : "Agent reached maximum iterations without final answer";
        state.setAgentResult(lastContent);

        return new ExecuteResult(state.getExecutionId(), "completed",
                lastContent, null, state.getCurrentIteration(),
                state.getTotalTokensUsed(), state.getToolCallCount(), null);
    }

    private void simulateToolExecution(ExecutionState state, String toolCallsJson) throws AiAgentException {
        try {
            JsonNode toolCalls = mapper.readTree(toolCallsJson);
            if (toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    String id = tc.path("id").asText("tool_call_" + UUID.randomUUID());
                    String name = tc.path("function").path("name").asText("unknown");
                    state.getMessages().add(ChatMessage.toolResult(id,
                            "{\"status\":\"success\",\"message\":\"Tool '" + name + "' executed (simulated)\"}"));
                }
            }
        } catch (Exception e) {
            throw new AiAgentException("Failed to parse tool calls: " + e.getMessage(), e);
        }
    }

    private String tryParseAsJson(String content) {
        if (content == null) return null;
        try {
            mapper.readTree(content);
            return content;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // --- Resume Agent ---

    /**
     * Resumes a paused agent execution after tool approval decision.
     */
    public ExecuteResult resumeAgent(String executionId, String approvalDecision,
                                      String modifiedToolParams) throws AiAgentException {
        ExecutionState state = ExecutionStore.get(executionId);
        if (state == null) {
            throw new AiAgentException("Execution not found: " + executionId);
        }
        if (!"paused".equals(state.getStatus())) {
            throw new AiAgentException("Execution is not paused (status: " + state.getStatus() + ")");
        }

        String pendingToolCalls = state.getPendingToolCall();

        if ("approve".equalsIgnoreCase(approvalDecision)) {
            // Execute the pending tool calls
            if (modifiedToolParams != null && !modifiedToolParams.isBlank()) {
                // Use modified params
                simulateToolExecution(state, modifiedToolParams);
            } else {
                simulateToolExecution(state, pendingToolCalls);
            }
            state.setStatus("running");
            state.setPendingToolCall(null);
            state.getTrace().add(new TraceEntry(state.getCurrentIteration(), "resumed",
                    "Tool call approved and executed", approvalDecision, 0));

        } else if ("reject".equalsIgnoreCase(approvalDecision)) {
            // Add rejection message and continue
            try {
                JsonNode toolCalls = mapper.readTree(pendingToolCalls);
                if (toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        String id = tc.path("id").asText("tool_call_" + UUID.randomUUID());
                        state.getMessages().add(ChatMessage.toolResult(id,
                                "{\"status\":\"rejected\",\"message\":\"Tool call was rejected by user\"}"));
                    }
                }
            } catch (Exception e) {
                throw new AiAgentException("Failed to process rejection: " + e.getMessage(), e);
            }
            state.setStatus("running");
            state.setPendingToolCall(null);
            state.getTrace().add(new TraceEntry(state.getCurrentIteration(), "resumed",
                    "Tool call rejected", approvalDecision, 0));

        } else if ("abort".equalsIgnoreCase(approvalDecision)) {
            state.setStatus("failed");
            state.setErrorMessage("Execution aborted by user");
            state.getTrace().add(new TraceEntry(state.getCurrentIteration(), "aborted",
                    "Execution aborted by user", null, 0));
            return new ExecuteResult(executionId, "failed",
                    "Execution aborted by user", null,
                    state.getCurrentIteration(), state.getTotalTokensUsed(),
                    state.getToolCallCount(), null);
        } else {
            throw new AiAgentException("Invalid approval decision: " + approvalDecision + ". Must be approve, reject, or abort.");
        }

        // Continue the ReAct loop
        String fullSystemPrompt = state.getConfiguration().getSystemPrompt();
        if (state.getConfiguration().getContextJson() != null) {
            fullSystemPrompt += "\n\n<context>\n" + state.getConfiguration().getContextJson() + "\n</context>";
        }
        return runReActLoop(state, fullSystemPrompt,
                state.getConfiguration().getToolsJson(),
                state.getConfiguration().getToolApprovalMode());
    }

    // --- Get Agent Status ---

    /**
     * Returns the current status of an agent execution.
     */
    public StatusResult getAgentStatus(String executionId) throws AiAgentException {
        ExecutionState state = ExecutionStore.get(executionId);
        if (state == null) {
            throw new AiAgentException("Execution not found: " + executionId);
        }
        return new StatusResult(
                executionId,
                state.getStatus(),
                state.getCurrentIteration(),
                state.getTotalTokensUsed(),
                state.getToolCallCount(),
                state.getElapsedTimeMs(),
                state.getAgentResult(),
                state.getErrorMessage(),
                state.getPendingToolCall()
        );
    }

    // --- Define Tools ---

    /**
     * Validates and stores a tool definition set for use in agent executions.
     */
    public DefineToolsResult defineTools(String toolDefinitionsJson, String toolSetName) throws AiAgentException {
        if (toolDefinitionsJson == null || toolDefinitionsJson.isBlank()) {
            throw new AiAgentException("toolDefinitionsJson is mandatory");
        }
        try {
            JsonNode tools = mapper.readTree(toolDefinitionsJson);
            if (!tools.isArray()) {
                throw new AiAgentException("toolDefinitionsJson must be a JSON array of tool definitions");
            }
            int toolCount = tools.size();
            List<String> toolNames = new ArrayList<>();
            for (JsonNode tool : tools) {
                String name = tool.path("function").path("name").asText(
                        tool.path("name").asText("unnamed"));
                toolNames.add(name);
            }

            String setName = (toolSetName != null && !toolSetName.isBlank())
                    ? toolSetName : "toolset-" + UUID.randomUUID().toString().substring(0, 8);

            return new DefineToolsResult(setName, toolCount, toolNames, toolDefinitionsJson);
        } catch (AiAgentException e) {
            throw e;
        } catch (Exception e) {
            throw new AiAgentException("Failed to parse tool definitions: " + e.getMessage(), e);
        }
    }

    // --- Get Trace ---

    /**
     * Returns the execution trace for a given execution.
     */
    public TraceResult getTrace(String executionId, boolean includeToolResponses) throws AiAgentException {
        ExecutionState state = ExecutionStore.get(executionId);
        if (state == null) {
            throw new AiAgentException("Execution not found: " + executionId);
        }

        List<TraceEntry> trace = state.getTrace();
        if (!includeToolResponses) {
            trace = trace.stream()
                    .filter(e -> !"tool_response".equals(e.phase()))
                    .toList();
        }

        try {
            String traceJson = mapper.writeValueAsString(trace);
            return new TraceResult(executionId, trace.size(), traceJson);
        } catch (Exception e) {
            throw new AiAgentException("Failed to serialize trace: " + e.getMessage(), e);
        }
    }

    // --- Result records ---

    public record ExecuteResult(String executionId, String status, String agentResult,
                                 String agentResultJson, int iterations, int tokensUsed,
                                 int toolCallCount, String pendingToolCall) {}

    public record StatusResult(String executionId, String status, int currentIteration,
                                int tokensUsed, int toolCallCount, long elapsedTimeMs,
                                String agentResult, String errorMessage, String pendingToolCall) {}

    public record DefineToolsResult(String toolSetName, int toolCount,
                                     List<String> toolNames, String validatedToolsJson) {}

    public record TraceResult(String executionId, int entryCount, String traceJson) {}
}
