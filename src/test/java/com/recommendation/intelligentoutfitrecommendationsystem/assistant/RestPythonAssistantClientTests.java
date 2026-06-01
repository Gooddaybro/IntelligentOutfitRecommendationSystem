package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.RestPythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatHistoryItem;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonUserContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
    void serializesPythonChatRequestUsingSnakeCaseContract() throws Exception {
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
                "req-client-test",
                "th_client_001",
                "th_client_001",
                "hello",
                List.of(new PythonChatHistoryItem("上一轮的问题", "上一轮的回答")),
                new PythonUserContext(
                        10L,
                        new BigDecimal("175.5"),
                        new BigDecimal("70.0"),
                        "male",
                        "regular",
                        List.of("commute"),
                        List.of("black"),
                        List.of(),
                        List.of("outerwear"),
                        null,
                        new BigDecimal("800.0")
                ),
                List.of(new PythonProductCandidate(
                        123L,
                        456L,
                        "秋季男士通勤外套",
                        "外套",
                        new BigDecimal("299.0"),
                        "in_stock",
                        "黑色",
                        "L",
                        null,
                        "棉",
                        "regular",
                        "autumn",
                        List.of("commute"),
                        null
                )),
                false
        );

        PythonChatResponse response = client.chat(request);

        assertThat(response.answer()).isEqualTo("ok");
        assertThat(response.recommendedSpuIds()).containsExactly(1001L);
        assertThat(requestBody.get())
                .contains("\"request_id\":\"req-client-test\"")
                .contains("\"session_id\":\"th_client_001\"")
                .contains("\"thread_id\":\"th_client_001\"")
                .contains("\"query\":\"hello\"")
                .contains("\"chat_history\"")
                .contains("\"user_query\":\"上一轮的问题\"")
                .contains("\"assistant_answer\":\"上一轮的回答\"")
                .contains("\"user_context\"")
                .contains("\"height_cm\":175.5")
                .contains("\"preferred_styles\":[\"commute\"]")
                .contains("\"budget_max\":800.0")
                .contains("\"candidates\"")
                .contains("\"spu_id\":123")
                .contains("\"sku_id\":456")
                .contains("\"sale_price\":299.0")
                .contains("\"stock_status\":\"in_stock\"")
                .doesNotContain("\"message\"")
                .doesNotContain("\"requestId\"")
                .doesNotContain("\"chatHistory\"");
    }
}
