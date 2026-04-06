package com.bonitasoft.connectors.aiagent;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for AI Agent Orchestrator connector.
 * Holds all connection and operation parameters.
 */
@Data
@Builder
public class AiAgentConfiguration {

    // Connection / Auth (Project/Runtime scope)
    private String llmProvider;
    private String llmApiKey;
    private String llmModel;
    private String llmBaseUrl;
    @Builder.Default
    private int maxIterations = 10;
    @Builder.Default
    private int maxTokenBudget = 100000;
    @Builder.Default
    private double temperature = 0.1;
    @Builder.Default
    private int connectTimeout = 30000;
    @Builder.Default
    private int readTimeout = 120000;

    // Execute Agent
    private String systemPrompt;
    private String userPrompt;
    private String toolsJson;
    private String contextJson;
    private String toolApprovalMode;

    // Resume Agent
    private String executionId;
    private String approvalDecision;
    private String modifiedToolParams;

    // Define Tools
    private String toolDefinitionsJson;
    private String toolSetName;

    // Get Trace
    @Builder.Default
    private boolean includeToolResponses = false;
}
