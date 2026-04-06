package com.bonitasoft.connectors.aiagent;

import lombok.extern.slf4j.Slf4j;
import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.util.Map;

/**
 * Abstract base connector for AI Agent Orchestrator.
 * Provides shared lifecycle: validate -> connect -> execute -> disconnect.
 */
@Slf4j
public abstract class AbstractAiAgentConnector extends AbstractConnector {

    protected static final String OUTPUT_SUCCESS = "success";
    protected static final String OUTPUT_ERROR_MESSAGE = "errorMessage";

    protected AiAgentConfiguration configuration;
    protected AiAgentClient client;

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        try {
            this.configuration = buildConfiguration();
            validateConfiguration(this.configuration);
        } catch (IllegalArgumentException e) {
            throw new ConnectorValidationException(this, e.getMessage());
        }
    }

    @Override
    public void connect() throws ConnectorException {
        try {
            this.client = new AiAgentClient(this.configuration);
            log.info("AiAgent connector connected successfully");
        } catch (AiAgentException e) {
            throw new ConnectorException("Failed to connect: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() throws ConnectorException {
        this.client = null;
    }

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        try {
            doExecute();
            setOutputParameter(OUTPUT_SUCCESS, true);
        } catch (AiAgentException e) {
            log.error("AiAgent connector execution failed: {}", e.getMessage(), e);
            setOutputParameter(OUTPUT_SUCCESS, false);
            setOutputParameter(OUTPUT_ERROR_MESSAGE, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in AiAgent connector: {}", e.getMessage(), e);
            setOutputParameter(OUTPUT_SUCCESS, false);
            setOutputParameter(OUTPUT_ERROR_MESSAGE, "Unexpected error: " + e.getMessage());
        }
    }

    protected abstract void doExecute() throws AiAgentException;

    protected abstract AiAgentConfiguration buildConfiguration();

    protected void validateConfiguration(AiAgentConfiguration config) {
        // Subclasses that require LLM connection should override and call validateLlmConfiguration
    }

    protected void validateLlmConfiguration(AiAgentConfiguration config) {
        if (config.getLlmProvider() == null || config.getLlmProvider().isBlank()) {
            throw new IllegalArgumentException("llmProvider is mandatory");
        }
        if (config.getLlmApiKey() == null || config.getLlmApiKey().isBlank()) {
            throw new IllegalArgumentException("llmApiKey is mandatory");
        }
        if (config.getLlmModel() == null || config.getLlmModel().isBlank()) {
            throw new IllegalArgumentException("llmModel is mandatory");
        }
    }

    protected String readStringInput(String name) {
        Object value = getInputParameter(name);
        return value != null ? value.toString() : null;
    }

    protected String readStringInput(String name, String defaultValue) {
        String value = readStringInput(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    protected Boolean readBooleanInput(String name, boolean defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? (Boolean) value : defaultValue;
    }

    protected Integer readIntegerInput(String name, int defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? ((Number) value).intValue() : defaultValue;
    }

    protected Double readDoubleInput(String name, double defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? ((Number) value).doubleValue() : defaultValue;
    }

    /**
     * Exposes output parameters for testing. Package-visible.
     */
    Map<String, Object> getOutputs() {
        return getOutputParameters();
    }
}
