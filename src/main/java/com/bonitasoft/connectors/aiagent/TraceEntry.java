package com.bonitasoft.connectors.aiagent;

/**
 * A single entry in the agent execution trace.
 */
public record TraceEntry(int iteration, String phase, String summary, String detail, int tokensUsed) {
}
