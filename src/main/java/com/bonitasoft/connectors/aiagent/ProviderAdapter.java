package com.bonitasoft.connectors.aiagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Sealed interface for LLM provider adapters.
 * Each adapter knows how to format requests and parse responses for its provider.
 */
public sealed interface ProviderAdapter permits
        ProviderAdapter.OpenAiAdapter,
        ProviderAdapter.AnthropicAdapter,
        ProviderAdapter.GeminiAdapter {

    /**
     * Send a chat completion request and return the LLM response.
     */
    LlmResponse chat(String systemPrompt, List<ChatMessage> messages, String toolsJson,
                      double temperature, int maxTokens) throws AiAgentException;

    /**
     * Create a provider adapter based on the configuration.
     */
    static ProviderAdapter create(AiAgentConfiguration config, HttpClient httpClient, ObjectMapper mapper) {
        return switch (config.getLlmProvider().toLowerCase()) {
            case "openai", "azure-openai" -> new OpenAiAdapter(config, httpClient, mapper);
            case "anthropic" -> new AnthropicAdapter(config, httpClient, mapper);
            case "google" -> new GeminiAdapter(config, httpClient, mapper);
            case "custom" -> new OpenAiAdapter(config, httpClient, mapper); // custom uses OpenAI-compatible API
            default -> throw new IllegalArgumentException("Unsupported LLM provider: " + config.getLlmProvider());
        };
    }

    /**
     * OpenAI-compatible adapter (also used for Azure OpenAI and custom endpoints).
     */
    @Slf4j
    final class OpenAiAdapter implements ProviderAdapter {

        private final AiAgentConfiguration config;
        private final HttpClient httpClient;
        private final ObjectMapper mapper;

        OpenAiAdapter(AiAgentConfiguration config, HttpClient httpClient, ObjectMapper mapper) {
            this.config = config;
            this.httpClient = httpClient;
            this.mapper = mapper;
        }

        @Override
        public LlmResponse chat(String systemPrompt, List<ChatMessage> messages, String toolsJson,
                                double temperature, int maxTokens) throws AiAgentException {
            try {
                String baseUrl = resolveBaseUrl();
                ObjectNode requestBody = mapper.createObjectNode();
                requestBody.put("model", config.getLlmModel());
                requestBody.put("temperature", temperature);
                requestBody.put("max_tokens", maxTokens);

                ArrayNode messagesArray = requestBody.putArray("messages");
                ObjectNode sysMsg = messagesArray.addObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);

                for (ChatMessage msg : messages) {
                    ObjectNode msgNode = messagesArray.addObject();
                    msgNode.put("role", msg.role());
                    if (msg.content() != null) {
                        msgNode.put("content", msg.content());
                    }
                    if (msg.toolCallId() != null) {
                        msgNode.put("tool_call_id", msg.toolCallId());
                    }
                    if (msg.toolCalls() != null) {
                        msgNode.set("tool_calls", mapper.readTree(msg.toolCalls()));
                    }
                }

                if (toolsJson != null && !toolsJson.isBlank()) {
                    requestBody.set("tools", mapper.readTree(toolsJson));
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.getLlmApiKey())
                        .timeout(Duration.ofMillis(config.getReadTimeout()))
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    boolean retryable = RetryPolicy.isRetryableStatusCode(response.statusCode());
                    throw new AiAgentException("OpenAI API error " + response.statusCode() + ": " + truncate(response.body()),
                            response.statusCode(), retryable);
                }

                return parseOpenAiResponse(response.body());
            } catch (AiAgentException e) {
                throw e;
            } catch (Exception e) {
                throw new AiAgentException("Failed to call OpenAI API: " + e.getMessage(), e);
            }
        }

        private String resolveBaseUrl() {
            if (config.getLlmBaseUrl() != null && !config.getLlmBaseUrl().isBlank()) {
                return config.getLlmBaseUrl().replaceAll("/+$", "");
            }
            if ("azure-openai".equalsIgnoreCase(config.getLlmProvider())) {
                return "https://models.inference.ai.azure.com/v1";
            }
            return "https://api.openai.com/v1";
        }

        private LlmResponse parseOpenAiResponse(String body) throws AiAgentException {
            try {
                JsonNode root = mapper.readTree(body);
                JsonNode choice = root.path("choices").path(0);
                JsonNode message = choice.path("message");

                String content = message.has("content") && !message.get("content").isNull()
                        ? message.get("content").asText() : null;
                String finishReason = choice.has("finish_reason") ? choice.get("finish_reason").asText() : "stop";
                String toolCalls = message.has("tool_calls") ? mapper.writeValueAsString(message.get("tool_calls")) : null;

                int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
                int completionTokens = root.path("usage").path("completion_tokens").asInt(0);

                return new LlmResponse(content, finishReason, toolCalls, promptTokens + completionTokens);
            } catch (Exception e) {
                throw new AiAgentException("Failed to parse OpenAI response: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Anthropic Claude adapter.
     */
    @Slf4j
    final class AnthropicAdapter implements ProviderAdapter {

        private final AiAgentConfiguration config;
        private final HttpClient httpClient;
        private final ObjectMapper mapper;

        AnthropicAdapter(AiAgentConfiguration config, HttpClient httpClient, ObjectMapper mapper) {
            this.config = config;
            this.httpClient = httpClient;
            this.mapper = mapper;
        }

        @Override
        public LlmResponse chat(String systemPrompt, List<ChatMessage> messages, String toolsJson,
                                double temperature, int maxTokens) throws AiAgentException {
            try {
                String baseUrl = config.getLlmBaseUrl() != null && !config.getLlmBaseUrl().isBlank()
                        ? config.getLlmBaseUrl().replaceAll("/+$", "")
                        : "https://api.anthropic.com";

                ObjectNode requestBody = mapper.createObjectNode();
                requestBody.put("model", config.getLlmModel());
                requestBody.put("max_tokens", maxTokens);
                requestBody.put("temperature", temperature);
                requestBody.put("system", systemPrompt);

                ArrayNode messagesArray = requestBody.putArray("messages");
                for (ChatMessage msg : messages) {
                    if ("system".equals(msg.role())) continue;
                    ObjectNode msgNode = messagesArray.addObject();
                    msgNode.put("role", msg.role());
                    if (msg.content() != null) {
                        msgNode.put("content", msg.content());
                    }
                    if (msg.toolCalls() != null) {
                        msgNode.set("content", mapper.readTree(msg.toolCalls()));
                    }
                }

                if (toolsJson != null && !toolsJson.isBlank()) {
                    requestBody.set("tools", mapper.readTree(toolsJson));
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/messages"))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", config.getLlmApiKey())
                        .header("anthropic-version", "2023-06-01")
                        .timeout(Duration.ofMillis(config.getReadTimeout()))
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    boolean retryable = RetryPolicy.isRetryableStatusCode(response.statusCode());
                    throw new AiAgentException("Anthropic API error " + response.statusCode() + ": " + truncate(response.body()),
                            response.statusCode(), retryable);
                }

                return parseAnthropicResponse(response.body());
            } catch (AiAgentException e) {
                throw e;
            } catch (Exception e) {
                throw new AiAgentException("Failed to call Anthropic API: " + e.getMessage(), e);
            }
        }

        private LlmResponse parseAnthropicResponse(String body) throws AiAgentException {
            try {
                JsonNode root = mapper.readTree(body);
                JsonNode content = root.path("content");
                String stopReason = root.path("stop_reason").asText("end_turn");

                StringBuilder textContent = new StringBuilder();
                ArrayNode toolCalls = mapper.createArrayNode();
                boolean hasToolUse = false;

                for (JsonNode block : content) {
                    String type = block.path("type").asText();
                    if ("text".equals(type)) {
                        textContent.append(block.path("text").asText());
                    } else if ("tool_use".equals(type)) {
                        hasToolUse = true;
                        ObjectNode toolCall = mapper.createObjectNode();
                        toolCall.put("id", block.path("id").asText());
                        toolCall.put("type", "function");
                        ObjectNode function = toolCall.putObject("function");
                        function.put("name", block.path("name").asText());
                        function.put("arguments", mapper.writeValueAsString(block.path("input")));
                        toolCalls.add(toolCall);
                    }
                }

                String finishReason = "tool_use".equals(stopReason) ? "tool_calls" : "stop";
                String toolCallsStr = hasToolUse ? mapper.writeValueAsString(toolCalls) : null;

                int inputTokens = root.path("usage").path("input_tokens").asInt(0);
                int outputTokens = root.path("usage").path("output_tokens").asInt(0);

                return new LlmResponse(
                        textContent.length() > 0 ? textContent.toString() : null,
                        finishReason,
                        toolCallsStr,
                        inputTokens + outputTokens
                );
            } catch (Exception e) {
                throw new AiAgentException("Failed to parse Anthropic response: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Google Gemini adapter.
     */
    @Slf4j
    final class GeminiAdapter implements ProviderAdapter {

        private final AiAgentConfiguration config;
        private final HttpClient httpClient;
        private final ObjectMapper mapper;

        GeminiAdapter(AiAgentConfiguration config, HttpClient httpClient, ObjectMapper mapper) {
            this.config = config;
            this.httpClient = httpClient;
            this.mapper = mapper;
        }

        @Override
        public LlmResponse chat(String systemPrompt, List<ChatMessage> messages, String toolsJson,
                                double temperature, int maxTokens) throws AiAgentException {
            try {
                String baseUrl = config.getLlmBaseUrl() != null && !config.getLlmBaseUrl().isBlank()
                        ? config.getLlmBaseUrl().replaceAll("/+$", "")
                        : "https://generativelanguage.googleapis.com";

                ObjectNode requestBody = mapper.createObjectNode();
                ObjectNode systemInstruction = requestBody.putObject("system_instruction");
                ObjectNode sysPart = systemInstruction.putArray("parts").addObject();
                sysPart.put("text", systemPrompt);

                ArrayNode contents = requestBody.putArray("contents");
                for (ChatMessage msg : messages) {
                    if ("system".equals(msg.role())) continue;
                    ObjectNode msgNode = contents.addObject();
                    msgNode.put("role", "assistant".equals(msg.role()) ? "model" : msg.role());
                    ObjectNode part = msgNode.putArray("parts").addObject();
                    if (msg.content() != null) {
                        part.put("text", msg.content());
                    }
                }

                ObjectNode genConfig = requestBody.putObject("generationConfig");
                genConfig.put("temperature", temperature);
                genConfig.put("maxOutputTokens", maxTokens);

                String url = baseUrl + "/v1beta/models/" + config.getLlmModel() + ":generateContent?key=" + config.getLlmApiKey();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofMillis(config.getReadTimeout()))
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    boolean retryable = RetryPolicy.isRetryableStatusCode(response.statusCode());
                    throw new AiAgentException("Gemini API error " + response.statusCode() + ": " + truncate(response.body()),
                            response.statusCode(), retryable);
                }

                return parseGeminiResponse(response.body());
            } catch (AiAgentException e) {
                throw e;
            } catch (Exception e) {
                throw new AiAgentException("Failed to call Gemini API: " + e.getMessage(), e);
            }
        }

        private LlmResponse parseGeminiResponse(String body) throws AiAgentException {
            try {
                JsonNode root = mapper.readTree(body);
                JsonNode candidate = root.path("candidates").path(0);
                JsonNode parts = candidate.path("content").path("parts");

                StringBuilder textContent = new StringBuilder();
                for (JsonNode part : parts) {
                    if (part.has("text")) {
                        textContent.append(part.get("text").asText());
                    }
                }

                int totalTokens = root.path("usageMetadata").path("totalTokenCount").asInt(0);

                return new LlmResponse(
                        textContent.length() > 0 ? textContent.toString() : null,
                        "stop",
                        null,
                        totalTokens
                );
            } catch (Exception e) {
                throw new AiAgentException("Failed to parse Gemini response: " + e.getMessage(), e);
            }
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
