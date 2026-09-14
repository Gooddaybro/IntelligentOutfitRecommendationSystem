package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.ProPythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProPythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProPythonAssistantClientTests {
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> payload = new AtomicReference<>();
    private final AtomicReference<String> token = new AtomicReference<>();
    private final ProPythonChatRequest request = new ProPythonChatRequest("assistant-v2", "pro", "req-1",
            "run-1", "th-1", "推荐外套", List.of(), Map.of("user_id", 7L),
            Map.of("budget_max", "299.90"), List.of(), "run-secret");

    private ProPythonAssistantClient server(int code, String response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/chat", exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return new ProPythonAssistantClient("http://127.0.0.1:" + server.getAddress().getPort(), 1000, 2000, "service-secret");
    }

    private String response(String runId) {
        return """
                {"contract_version":"assistant-v2","agent_mode":"pro","request_id":"req-1",
                 "run_id":"%s","thread_id":"th-1","answer":"需补充信息",
                 "product_refs":[],"requirements":[],"stop_reason":"needs_input",
                 "metrics":{"decisions":1,"tool_calls":0,"elapsed_ms":1,"token_usage":null}}
                """.formatted(runId);
    }

    @AfterEach
    void stop() { if (server != null) { server.stop(0); } }

    @Test
    void sendsOnlyV2SnakeCaseAndKeepsCredentialOutOfDiagnostics() throws Exception {
        var result = server(200, response("run-1")).chat(request);
        var body = mapper.readTree(payload.get());
        assertThat(token.get()).isEqualTo("service-secret");
        assertThat(body.path("contract_version").asText()).isEqualTo("assistant-v2");
        assertThat(body.path("explicit_filters").path("budget_max").asText()).isEqualTo("299.90");
        assertThat(body.path("tool_run_token").asText()).isEqualTo("run-secret");
        assertThat(body.has("demand_intent")).isFalse();
        assertThat(body.has("candidates")).isFalse();
        assertThat(request.toString()).doesNotContain("run-secret");
        assertThat(result.path("run_id").asText()).isEqualTo("run-1");
    }

    @Test
    void rejectsResponseFromAnotherRun() throws Exception {
        var client = server(200, response("foreign"));
        assertThrows(ExternalServiceException.class, () -> client.chat(request));
    }

    @Test
    void failureDoesNotLeakUpstreamBodyOrCredentials() throws Exception {
        var client = server(500, "private: run-secret service-secret");
        var error = assertThrows(ExternalServiceException.class, () -> client.chat(request));
        assertThat(error.toString()).doesNotContain("run-secret", "service-secret", "private:");
        assertThat(error.getCause()).isNull();
    }

    @Test
    void rejectsMalformedSuccessfulResponse() throws Exception {
        var client = server(200, "{\"answer\":\"not the contract\"}");
        assertThrows(ExternalServiceException.class, () -> client.chat(request));
    }
}
