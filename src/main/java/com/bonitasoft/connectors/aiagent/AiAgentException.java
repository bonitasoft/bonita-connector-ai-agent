package com.bonitasoft.connectors.aiagent;

/**
 * Typed exception for AI Agent Orchestrator connector.
 */
public class AiAgentException extends Exception {

    private final int statusCode;
    private final boolean retryable;

    public AiAgentException(String message) {
        super(message);
        this.statusCode = -1;
        this.retryable = false;
    }

    public AiAgentException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.retryable = false;
    }

    public AiAgentException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public AiAgentException(String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
