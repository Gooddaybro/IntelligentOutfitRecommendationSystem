package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseResponse;
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

/** HTTP client for the isolated Python demand-intent parser. */
@Component
public class RestDemandIntentParseClient implements DemandIntentParseClient {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final HttpClient httpClient;
    private final String parseUrl;
    private final String internalApiToken;

    public RestDemandIntentParseClient(
            @Value("${app.ai.python-base-url}") String pythonBaseUrl,
            @Value("${app.ai.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${app.internal-api.token}") String internalApiToken
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.parseUrl = pythonBaseUrl.replaceAll("/+$", "") + "/internal/demand-intent/parse";
        this.internalApiToken = internalApiToken;
    }

    @Override
    public Optional<LlmDemandParseResponse> parse(LlmDemandParseRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(parseUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalApiToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException("demand intent parser returned status " + response.statusCode());
            }
            return Optional.of(objectMapper.readValue(response.body(), LlmDemandParseResponse.class));
        } catch (IOException exception) {
            throw new ExternalServiceException("failed to call demand intent parser", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("demand intent parser was interrupted", exception);
        } catch (IllegalArgumentException exception) {
            throw new ExternalServiceException("demand intent parser URL is invalid", exception);
        }
    }
}
