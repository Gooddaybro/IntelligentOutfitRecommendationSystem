package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProProgressEvent;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Synchronous v2 transport with no Lite fallback, redirects, or automatic replay.
 * Matching identifiers prove response association, not product or answer correctness.
 * Synchronous and SSE transports share the same identifier and payload validation; the service
 * still owns final product validation and persistence.
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

    /**
     * Streams only structurally valid v2 events to the Pro service.
     *
     * <p>Foreign progress and terminal events are ignored.  If the upstream stream then ends
     * without a matching terminal event, the handler receives one stable incomplete error.</p>
     *
     * @param request Java-owned v2 request
     * @param handler callback for validated progress, token and terminal events
     */
    public void streamChat(ProPythonChatRequest request, ProStreamHandler handler) {
        if (internalToken == null || internalToken.isBlank()) {
            handler.onError("python_stream_unavailable", "Pro Python service is unavailable");
            return;
        }
        try {
            HttpRequest outgoing = HttpRequest.newBuilder(streamEndpoint())
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request))).build();
            HttpResponse<Stream<String>> response = client.send(outgoing, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                close(response.body());
                handler.onError("python_stream_unavailable", "Pro Python service is unavailable");
                return;
            }
            forwardSse(response.body(), request, handler);
        } catch (IOException error) {
            handler.onError("python_stream_unavailable", "Pro Python service is unavailable");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            handler.onError("python_stream_interrupted", "Pro Python stream was interrupted");
        } catch (IllegalArgumentException error) {
            handler.onError("python_stream_invalid_url", "Pro Python service is unavailable");
        }
    }

    /** Alias kept for callers that name the operation after the endpoint rather than the client. */
    public void stream(ProPythonChatRequest request, ProStreamHandler handler) {
        streamChat(request, handler);
    }

    private URI streamEndpoint() {
        return URI.create(endpoint.toString().replace("/v2/chat", "/v2/chat/stream"));
    }

    private void forwardSse(Stream<String> lines, ProPythonChatRequest request, ProStreamHandler handler) {
        PythonSseEventParser parser = new PythonSseEventParser();
        AtomicLong lastSequence = new AtomicLong(0);
        AtomicBoolean terminal = new AtomicBoolean(false);
        try (lines) {
            lines.map(parser::accept)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(event -> forwardEvent(event, request, handler, lastSequence, terminal));
        }
        if (!terminal.get()) {
            handler.onError("python_stream_incomplete", "Pro Python stream ended unexpectedly");
        }
    }

    private void forwardEvent(PythonSseEvent event, ProPythonChatRequest request, ProStreamHandler handler,
                               AtomicLong lastSequence, AtomicBoolean terminal) {
        if (terminal.get()) {
            return;
        }
        try {
            JsonNode data = mapper.readTree(event.data());
            switch (event.event()) {
                case "progress" -> forwardProgress(data, request, handler, lastSequence);
                case "token" -> forwardToken(data, handler);
                case "done" -> {
                    if (matches(data, request) && terminal.compareAndSet(false, true)) {
                        handler.onDone(data);
                    }
                }
                case "error" -> forwardError(data, request, handler, terminal);
                default -> {
                }
            }
        } catch (IOException | RuntimeException error) {
            if (terminal.compareAndSet(false, true)) {
                handler.onError("python_stream_parse_error", "Pro Python stream returned invalid data");
            }
        }
    }

    private void forwardProgress(JsonNode data, ProPythonChatRequest request, ProStreamHandler handler,
                                 AtomicLong lastSequence) {
        String runId = data.path("run_id").asText("");
        long sequence = data.path("sequence").asLong(-1);
        String tool = data.path("tool").asText("");
        String stage = data.path("stage").asText("");
        String message = data.path("message").asText("");
        if (!request.runId().equals(runId) || sequence <= 0 || sequence <= lastSequence.get()
                || tool.isBlank() || tool.length() > 100 || !("started".equals(stage) || "completed".equals(stage))
                || message.isBlank() || message.length() > 240) {
            return;
        }
        if (lastSequence.compareAndSet(lastSequence.get(), sequence)) {
            handler.onProgress(new ProProgressEvent(runId, sequence, tool, stage, message));
        }
    }

    private void forwardToken(JsonNode data, ProStreamHandler handler) {
        if (data == null || !data.path("content").isTextual()) {
            return;
        }
        String content = data.path("content").asText();
        if (!content.isEmpty() && content.length() <= 2000) {
            handler.onToken(content);
        }
    }

    private void forwardError(JsonNode data, ProPythonChatRequest request, ProStreamHandler handler,
                              AtomicBoolean terminal) {
        String runId = data.path("run_id").asText("");
        if (!runId.isBlank() && !request.runId().equals(runId)) {
            return;
        }
        if (terminal.compareAndSet(false, true)) {
            handler.onError("python_stream_error", "Pro Python stream failed");
        }
    }

    private void close(Stream<String> lines) {
        if (lines != null) {
            lines.close();
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

    /** Pro v2 stream callbacks after transport-level filtering. */
    public interface ProStreamHandler {
        void onProgress(ProProgressEvent event);

        void onToken(String content);

        void onDone(JsonNode result);

        void onError(String code, String message);
    }

    private static ExternalServiceException unavailable() {
        return new ExternalServiceException("Pro Python service is unavailable or returned an invalid response");
    }
}
