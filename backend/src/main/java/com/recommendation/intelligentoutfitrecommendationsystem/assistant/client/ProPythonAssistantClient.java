package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProPythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * Synchronous v2 transport with no Lite fallback, redirects, or automatic replay.
 * Matching identifiers prove response association, not product or answer correctness.
 * Its result remains internal until the separate final-validation stage is implemented.
 */
@Component
public class ProPythonAssistantClient {
    private static final Set<String> STOP_REASONS = Set.of("completed", "needs_input", "budget_exhausted",
            "validation_failed", "dependency_unavailable", "invalid_response", "cancelled");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HttpClient client;
    private final URI endpoint;
    private final Duration readTimeout;
    private final String internalToken;

    public ProPythonAssistantClient(
            @Value("${app.ai.python-base-url}") String baseUrl,
            @Value("${app.ai.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${app.ai.pro-read-timeout-ms:110000}") long readTimeoutMs,
            @Value("${app.ai.python-internal-token:${app.internal-api.token}}") String internalToken) {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0 || readTimeoutMs > 120000) {
            throw new IllegalArgumentException("Pro transport requires positive bounded timeouts");
        }
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v2/chat");
        if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("Pro transport requires a configured HTTP service address");
        }
        this.internalToken = internalToken;
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Sends a Java-assembled run exactly once; upstream diagnostics never enter public exceptions. */
    public JsonNode chat(ProPythonChatRequest request) {
        if (internalToken == null || internalToken.isBlank()) {
            throw unavailable();
        }
        try {
            HttpRequest outgoing = HttpRequest.newBuilder(endpoint).timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request))).build();
            HttpResponse<String> response = client.send(outgoing, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 1_048_576) {
                throw unavailable();
            }
            JsonNode result = mapper.readTree(response.body());
            if (!matches(result, request)) {
                throw unavailable();
            }
            return result;
        } catch (IOException | IllegalArgumentException error) {
            throw unavailable();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static boolean matches(JsonNode result, ProPythonChatRequest request) {
        return result != null && result.isObject()
                && "assistant-v2".equals(result.path("contract_version").asText())
                && "pro".equals(result.path("agent_mode").asText())
                && request.requestId().equals(result.path("request_id").asText())
                && request.runId().equals(result.path("run_id").asText())
                && request.threadId().equals(result.path("thread_id").asText())
                && result.path("answer").isTextual() && !result.path("answer").asText().isBlank()
                && result.path("answer").asText().length() <= 8000
                && result.path("product_refs").isArray() && result.path("product_refs").size() <= 20
                && result.path("requirements").isArray() && result.path("requirements").size() <= 12
                && result.path("metrics").isObject()
                && STOP_REASONS.contains(result.path("stop_reason").asText())
                && !result.has("tool_run_token") && !result.has("user_context");
    }

    private static ExternalServiceException unavailable() {
        return new ExternalServiceException("Pro Python service is unavailable or returned an invalid response");
    }
}
