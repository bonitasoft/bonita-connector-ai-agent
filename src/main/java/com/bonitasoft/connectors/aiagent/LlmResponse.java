package com.bonitasoft.connectors.aiagent;

/**
 * Parsed response from an LLM provider.
 */
public record LlmResponse(String content, String finishReason, String toolCalls, int tokensUsed) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isBlank() && "tool_calls".equals(finishReason);
    }
}
