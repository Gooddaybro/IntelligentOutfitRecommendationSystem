package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

/**
 * Python AI 服务的同步 HTTP 客户端。
 *
 * 第一版只调用 `/chat` JSON 接口；SSE 流式返回和异步任务后续会新增独立客户端能力。
 */
@Component
public class RestPythonAssistantClient implements PythonAssistantClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String chatUrl;
    private final Duration readTimeout;

    public RestPythonAssistantClient(
            @Value("${app.ai.python-base-url}") String pythonBaseUrl,
            @Value("${app.ai.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${app.ai.read-timeout-ms}") long readTimeoutMs
    ) {
        // Python 请求包含 LocalDateTime 聊天历史，必须注册 JavaTime 模块并使用 ISO 字符串格式保持跨服务契约稳定。
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.chatUrl = pythonBaseUrl.replaceAll("/+$", "") + "/chat";
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
    }

    @Override
    public PythonChatResponse chat(PythonChatRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(chatUrl))
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
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
}
