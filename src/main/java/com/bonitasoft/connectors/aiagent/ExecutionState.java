package com.bonitasoft.connectors.aiagent;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the mutable state of an agent execution session.
 */
public class ExecutionState {

    private final String executionId;
    private final AiAgentConfiguration configuration;
    private final List<ChatMessage> messages;
    private final List<TraceEntry> trace;
    private String status; // running, paused, completed, failed
    private int currentIteration;
    private int totalTokensUsed;
    private int toolCallCount;
    private String pendingToolCall;
    private String agentResult;
    private String agentResultJson;
    private String errorMessage;
    private final long startTimeMs;

    public ExecutionState(String executionId, AiAgentConfiguration configuration) {
        this.executionId = executionId;
        this.configuration = configuration;
        this.messages = new ArrayList<>();
        this.trace = new ArrayList<>();
        this.status = "running";
        this.currentIteration = 0;
        this.totalTokensUsed = 0;
        this.toolCallCount = 0;
        this.startTimeMs = System.currentTimeMillis();
    }

    public String getExecutionId() { return executionId; }
    public AiAgentConfiguration getConfiguration() { return configuration; }
    public List<ChatMessage> getMessages() { return messages; }
    public List<TraceEntry> getTrace() { return trace; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getCurrentIteration() { return currentIteration; }
    public void setCurrentIteration(int currentIteration) { this.currentIteration = currentIteration; }
    public int getTotalTokensUsed() { return totalTokensUsed; }
    public void addTokensUsed(int tokens) { this.totalTokensUsed += tokens; }
    public int getToolCallCount() { return toolCallCount; }
    public void incrementToolCallCount() { this.toolCallCount++; }
    public String getPendingToolCall() { return pendingToolCall; }
    public void setPendingToolCall(String pendingToolCall) { this.pendingToolCall = pendingToolCall; }
    public String getAgentResult() { return agentResult; }
    public void setAgentResult(String agentResult) { this.agentResult = agentResult; }
    public String getAgentResultJson() { return agentResultJson; }
    public void setAgentResultJson(String agentResultJson) { this.agentResultJson = agentResultJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getElapsedTimeMs() { return System.currentTimeMillis() - startTimeMs; }
}
