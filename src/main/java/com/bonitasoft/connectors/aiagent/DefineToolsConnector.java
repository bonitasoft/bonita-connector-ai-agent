package com.bonitasoft.connectors.aiagent;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates and registers a set of tool definitions for use in AI Agent executions.
 * Does not require LLM connection - validates JSON locally.
 */
@Slf4j
public class DefineToolsConnector extends AbstractAiAgentConnector {

    // === Input parameter name constants ===
    static final String INPUT_TOOL_DEFINITIONS_JSON = "toolDefinitionsJson";
    static final String INPUT_TOOL_SET_NAME = "toolSetName";

    // === Output parameter name constants ===
    static final String OUTPUT_TOOL_SET_NAME = "toolSetName";
    static final String OUTPUT_TOOL_COUNT = "toolCount";
    static final String OUTPUT_TOOL_NAMES = "toolNames";
    static final String OUTPUT_VALIDATED_TOOLS_JSON = "validatedToolsJson";

    @Override
    protected AiAgentConfiguration buildConfiguration() {
        return AiAgentConfiguration.builder()
                .llmProvider("none")
                .llmApiKey("none")
                .llmModel("none")
                .toolDefinitionsJson(readStringInput(INPUT_TOOL_DEFINITIONS_JSON))
                .toolSetName(readStringInput(INPUT_TOOL_SET_NAME))
                .build();
    }

    @Override
    protected void validateConfiguration(AiAgentConfiguration config) {
        if (config.getToolDefinitionsJson() == null || config.getToolDefinitionsJson().isBlank()) {
            throw new IllegalArgumentException("toolDefinitionsJson is mandatory");
        }
    }

    @Override
    protected void doExecute() throws AiAgentException {
        log.info("Executing DefineTools connector");

        AiAgentClient.DefineToolsResult result = client.defineTools(
                configuration.getToolDefinitionsJson(),
                configuration.getToolSetName()
        );

        setOutputParameter(OUTPUT_TOOL_SET_NAME, result.toolSetName());
        setOutputParameter(OUTPUT_TOOL_COUNT, result.toolCount());
        setOutputParameter(OUTPUT_TOOL_NAMES, String.join(", ", result.toolNames()));
        setOutputParameter(OUTPUT_VALIDATED_TOOLS_JSON, result.validatedToolsJson());

        log.info("DefineTools connector completed: {} tools in set '{}'",
                result.toolCount(), result.toolSetName());
    }
}
