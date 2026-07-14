package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
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
import java.util.stream.Stream;

/**
 * Python AI 服务的 HTTP 客户端。
 *
 * 同步 `/chat` 和流式 `/chat/stream` 共用同一套 Python 请求序列化配置，确保跨服务字段命名一致。
 */
@Component
public class RestPythonAssistantClient implements PythonAssistantClient, PythonAssistantStreamClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String chatUrl;
    private final String streamChatUrl;
    private final Duration readTimeout;
    private final Duration streamReadTimeout;
    private final String internalToken;

    public RestPythonAssistantClient(
            @Value("${app.ai.python-base-url}") String pythonBaseUrl,
            @Value("${app.ai.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${app.ai.read-timeout-ms}") long readTimeoutMs,
            @Value("${app.ai.stream-read-timeout-ms:${app.ai.stream-timeout-ms:120000}}") long streamReadTimeoutMs,
            @Value("${app.ai.python-internal-token:${app.internal-api.token}}") String internalToken
    ) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException("python assistant internal token must not be blank");
        }
        // Python 请求包含 LocalDateTime 聊天历史，必须注册 JavaTime 模块并使用 ISO 字符串格式保持跨服务契约稳定。
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.chatUrl = pythonBaseUrl.replaceAll("/+$", "") + "/chat";
        this.streamChatUrl = pythonBaseUrl.replaceAll("/+$", "") + "/chat/stream";
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.streamReadTimeout = Duration.ofMillis(streamReadTimeoutMs);
        this.internalToken = internalToken;
    }

    @Override
    public PythonChatResponse chat(PythonChatRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(chatUrl))
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException("python assistant returned status " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), PythonChatResponse.class);
        } catch (IOException exception) {
            throw new ExternalServiceException("failed to call python assistant", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("python assistant call was interrupted", exception);
        } catch (IllegalArgumentException exception) {
            throw new ExternalServiceException("python assistant url is invalid", exception);
        }
    }

    @Override
    public void streamChat(PythonChatRequest request, PythonAssistantStreamHandler handler) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(streamChatUrl))
                    .timeout(streamReadTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                handler.onError("python_stream_unavailable", "python assistant returned status " + response.statusCode());
                return;
            }
            forwardSseLines(response.body(), handler);
        } catch (IOException exception) {
            handler.onError("python_stream_unavailable", "failed to call python assistant stream");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            handler.onError("python_stream_interrupted", "python assistant stream was interrupted");
        } catch (IllegalArgumentException exception) {
            handler.onError("python_stream_invalid_url", "python assistant stream url is invalid");
        }
    }

    private void forwardSseLines(Stream<String> lines, PythonAssistantStreamHandler handler) {
        PythonSseEventParser parser = new PythonSseEventParser();
        try (lines) {
            lines.map(parser::accept)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(event -> forwardEvent(event, handler));
        }
    }

    private void forwardEvent(PythonSseEvent event, PythonAssistantStreamHandler handler) {
        try {
            switch (event.event()) {
                case "token" -> handler.onToken(objectMapper.readTree(event.data()).path("content").asText(""));
                case "done" -> handler.onDone(objectMapper.readValue(event.data(), PythonChatResponse.class));
                case "error" -> forwardErrorEvent(event, handler);
                default -> {
                }
            }
        } catch (IOException exception) {
            handler.onError("python_stream_parse_error", "failed to parse python assistant stream event");
        }
    }

    private void forwardErrorEvent(PythonSseEvent event, PythonAssistantStreamHandler handler) throws IOException {
        JsonNode data = objectMapper.readTree(event.data());
        String code = data.path("code").asText("python_stream_error");
        String message = data.path("message").asText("python assistant stream failed");
        handler.onError(code, message);
    }
}
