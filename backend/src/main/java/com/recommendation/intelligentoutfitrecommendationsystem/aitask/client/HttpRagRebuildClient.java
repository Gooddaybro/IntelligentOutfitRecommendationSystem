package com.recommendation.intelligentoutfitrecommendationsystem.aitask.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 供 Worker 使用的 Python 内部重建 HTTP 客户端，使用独立长任务读取超时和内部令牌。
 */
@Component
@Profile({"worker", "test"})
public class HttpRagRebuildClient implements RagRebuildClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final URI rebuildUri;
    private final Duration readTimeout;
    private final String internalToken;

    public HttpRagRebuildClient(
            @Value("${app.ai.python-base-url}") String pythonBaseUrl,
            @Value("${app.ai.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${app.ai-task.python-read-timeout-ms:300000}") long readTimeoutMs,
            @Value("${app.ai.python-internal-token:${app.internal-api.token}}") String internalToken
    ) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException("Python internal token must not be blank");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.rebuildUri = URI.create(pythonBaseUrl.replaceAll("/+$", "") + "/internal/rag/rebuild");
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.internalToken = internalToken;
    }

    @Override
    public RagRebuildResult rebuild(String taskId, String correlationId, String traceparent) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(rebuildUri)
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken);
            addHeader(builder, "X-Request-Id", correlationId);
            addHeader(builder, "traceparent", traceparent);
            String body = objectMapper.writeValueAsString(Map.of(
                    "taskId", taskId,
                    "source", "LOCAL_GLOBAL_KNOWLEDGE"
            ));
            HttpResponse<String> response = httpClient.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            validateStatus(response.statusCode());
            RagRebuildResult result = objectMapper.readValue(response.body(), RagRebuildResult.class);
            if (!taskId.equals(result.taskId())) {
                throw new RagRebuildClientException(
                        "PYTHON_RESPONSE_MISMATCH", false, "Python rebuild response taskId does not match"
                );
            }
            return result;
        } catch (RagRebuildClientException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new RagRebuildClientException(
                    "PYTHON_IO", true, "failed to call Python rebuild endpoint", exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RagRebuildClientException(
                    "PYTHON_INTERRUPTED", true, "Python rebuild call was interrupted", exception
            );
        }
    }

    private void validateStatus(int status) {
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401 || status == 403) {
            throw new RagRebuildClientException("PYTHON_AUTH", false, "Python internal authentication failed");
        }
        if (status == 429 || status >= 500) {
            throw new RagRebuildClientException("PYTHON_RETRYABLE", true, "Python rebuild is unavailable");
        }
        throw new RagRebuildClientException("PYTHON_REJECTED", false, "Python rejected rebuild request");
    }

    private void addHeader(HttpRequest.Builder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.header(name, value);
        }
    }
}
