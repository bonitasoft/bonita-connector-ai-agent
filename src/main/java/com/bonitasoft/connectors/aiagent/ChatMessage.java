package com.bonitasoft.connectors.aiagent;

/**
 * Represents a message in the LLM conversation history.
 */
public record ChatMessage(String role, String content, String toolCallId, String toolCalls) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    public static ChatMessage assistantWithToolCalls(String content, String toolCalls) {
        return new ChatMessage("assistant", content, null, toolCalls);
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null);
    }
}
