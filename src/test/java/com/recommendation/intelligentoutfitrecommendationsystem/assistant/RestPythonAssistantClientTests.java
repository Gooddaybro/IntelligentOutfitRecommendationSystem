package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.RestPythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestPythonAssistantClientTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void serializesChatHistoryWithJavaTimeAndReadsPythonResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {
                      "answer": "ok",
                      "recommendedSpuIds": [1001]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        RestPythonAssistantClient client = new RestPythonAssistantClient(baseUrl, 1000, 5000);
        PythonChatRequest request = new PythonChatRequest(
                "th_client_001",
                10L,
                "hello",
                null,
                null,
                null,
                List.of(new MessageResponse(
                        "user",
                        "hello",
                        "succeeded",
                        "req-client-test",
                        LocalDateTime.of(2026, 5, 28, 9, 30)
                )),
                List.of()
        );

        PythonChatResponse response = client.chat(request);

        assertThat(response.answer()).isEqualTo("ok");
        assertThat(response.recommendedSpuIds()).containsExactly(1001L);
        assertThat(requestBody.get()).contains("\"createdAt\":\"2026-05-28T09:30:00\"");
    }
}
