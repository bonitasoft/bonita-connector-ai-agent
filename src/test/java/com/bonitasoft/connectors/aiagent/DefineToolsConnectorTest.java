package com.bonitasoft.connectors.aiagent;

import static org.assertj.core.api.Assertions.*;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class DefineToolsConnectorTest {

    private DefineToolsConnector connector;

    @BeforeEach
    void setUp() {
        connector = new DefineToolsConnector();
    }

    @Test
    void shouldValidateAndReturnToolDefinitions() throws Exception {
        String toolsJson = "[{\"type\":\"function\",\"function\":{\"name\":\"search\",\"description\":\"Search the web\",\"parameters\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}}}]";

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("toolDefinitionsJson", toolsJson);
        inputs.put("toolSetName", "my-tools");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("toolSetName")).isEqualTo("my-tools");
        assertThat(outputs.get("toolCount")).isEqualTo(1);
        assertThat((String) outputs.get("toolNames")).contains("search");
        assertThat(outputs.get("validatedToolsJson")).isNotNull();
    }

    @Test
    void shouldAutoGenerateToolSetNameWhenNotProvided() throws Exception {
        String toolsJson = "[{\"type\":\"function\",\"function\":{\"name\":\"calculate\",\"description\":\"Calculate\"}}]";

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("toolDefinitionsJson", toolsJson);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat((String) outputs.get("toolSetName")).startsWith("toolset-");
    }

    @Test
    void shouldFailValidationWhenToolDefinitionsJsonMissing() {
        Map<String, Object> inputs = new HashMap<>();
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("toolDefinitionsJson is mandatory");
    }

    @Test
    void shouldFailOnInvalidJson() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("toolDefinitionsJson", "not-valid-json");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat(outputs.get("errorMessage")).isNotNull();
    }

    @Test
    void shouldFailOnNonArrayJson() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("toolDefinitionsJson", "{\"not\":\"an array\"}");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("JSON array");
    }

    @Test
    void shouldHandleMultipleTools() throws Exception {
        String toolsJson = "[" +
                "{\"type\":\"function\",\"function\":{\"name\":\"search\",\"description\":\"Search\"}}," +
                "{\"type\":\"function\",\"function\":{\"name\":\"calculate\",\"description\":\"Calculate\"}}," +
                "{\"type\":\"function\",\"function\":{\"name\":\"translate\",\"description\":\"Translate\"}}" +
                "]";

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("toolDefinitionsJson", toolsJson);
        inputs.put("toolSetName", "multi-tool-set");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("toolCount")).isEqualTo(3);
        assertThat((String) outputs.get("toolNames")).contains("search", "calculate", "translate");
    }
}
