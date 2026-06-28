package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.RestPythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamHandler;
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
import java.util.ArrayList;
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
                      "request_id": "req-client-test",
                      "answer": "ok",
                      "intent": "recommendation",
                      "product_refs": [
                        {
                          "spu_id": 1001,
                          "sku_id": 2001,
                          "reason": "fits the requested commute style",
                          "rank_score": 0.95
                        }
                      ],
                      "suggested_actions": []
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
                        List.of("autumn"),
                        List.of("commute"),
                        null,
                        "SPU-123",
                        "SKU-456",
                        7,
                        List.of("适用场景:通勤")
                )),
                false
        );

        PythonChatResponse response = client.chat(request);

        assertThat(response.requestId()).isEqualTo("req-client-test");
        assertThat(response.answer()).isEqualTo("ok");
        assertThat(response.intent()).isEqualTo("recommendation");
        assertThat(response.productRefs())
                .extracting("spuId", "skuId", "reason")
                .containsExactly(org.assertj.core.api.Assertions.tuple(
                        1001L,
                        2001L,
                        "fits the requested commute style"
                ));
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
                .contains("\"season\":[\"autumn\"]")
                .doesNotContain("\"message\"")
                .doesNotContain("\"requestId\"")
                .doesNotContain("\"chatHistory\"");
    }

    @Test
    void streamsPythonTokensAndDoneResponseFromStreamEndpoint() throws Exception {
        AtomicReference<String> acceptHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/stream", exchange -> {
            acceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    event: token
                    data: {"content":"我建议"}

                    event: done
                    data: {"request_id":"req-stream-test","answer":"我建议您穿 L 码。","intent":"size_recommendation","product_refs":[{"spu_id":1001,"sku_id":2001,"reason":"尺码匹配","rank_score":0.93}]}

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        RestPythonAssistantClient client = new RestPythonAssistantClient(baseUrl, 1000, 5000);
        CapturingStreamHandler handler = new CapturingStreamHandler();

        client.streamChat(minimalPythonRequest(), handler);

        assertThat(acceptHeader.get()).isEqualTo("text/event-stream");
        assertThat(requestBody.get())
                .contains("\"request_id\":\"req-stream-test\"")
                .contains("\"query\":\"hello\"");
        assertThat(handler.tokens).containsExactly("我建议");
        assertThat(handler.done).isNotNull();
        assertThat(handler.done.answer()).isEqualTo("我建议您穿 L 码。");
        assertThat(handler.done.productRefs())
                .extracting("spuId", "skuId", "reason")
                .containsExactly(org.assertj.core.api.Assertions.tuple(1001L, 2001L, "尺码匹配"));
        assertThat(handler.errors).isEmpty();
    }

    private PythonChatRequest minimalPythonRequest() {
        return new PythonChatRequest(
                "req-stream-test",
                "th_stream_001",
                "th_stream_001",
                "hello",
                List.of(),
                new PythonUserContext(
                        10L,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null
                ),
                List.of(),
                false
        );
    }

    private static class CapturingStreamHandler implements PythonAssistantStreamHandler {
        private final List<String> tokens = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private PythonChatResponse done;

        @Override
        public void onToken(String content) {
            tokens.add(content);
        }

        @Override
        public void onDone(PythonChatResponse response) {
            done = response;
        }

        @Override
        public void onError(String code, String message) {
            errors.add(code + ":" + message);
        }
    }
}
