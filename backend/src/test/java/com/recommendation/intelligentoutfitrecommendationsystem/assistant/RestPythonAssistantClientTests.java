package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.RestPythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamHandler;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintStrength;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;
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
import java.util.Set;
import java.util.TreeSet;
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
        AtomicReference<String> internalTokenHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            internalTokenHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
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
                          "rank_score": 0.95,
                          "matched_dimensions": [],
                          "outfit_role": "TOP"
                        }
                      ],
                      "rejected_reasons": {
                        "HARD_FILTER_MISMATCH": 2,
                        "SIZE_MISMATCH": 1,
                        "LOW_STYLE_SCORE": 3,
                        "MISSING_REQUIRED_EVIDENCE": 4
                      },
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
        RestPythonAssistantClient client = new RestPythonAssistantClient(
                baseUrl,
                1000,
                5000,
                "test-internal-token"
        );
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
                EffectiveDemand.v3(
                        "hello",
                        "recommendation",
                        List.of("PRODUCT_RECOMMENDATION"),
                        List.of(new IntentConstraint(
                                "turn-1-season", "season", ConstraintOperator.EQUALS, List.of("SUMMER"),
                                ConstraintStrength.HARD, ConstraintOrigin.USER_EXPLICIT, "turn-1", null,
                                "ACTIVE_DEMAND", null
                        )),
                        List.of(),
                        null
                ),
                false
        );

        PythonChatResponse response = client.chat(request);

        assertThat(response.requestId()).isEqualTo("req-client-test");
        assertThat(response.answer()).isEqualTo("ok");
        assertThat(response.intent()).isEqualTo("recommendation");
        assertThat(response.productRefs()).singleElement().satisfies(ref ->
                assertThat(ref.outfitRole()).isEqualTo("TOP"));
        assertThat(response.rejectedReasons()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "HARD_FILTER_MISMATCH", 2,
                "SIZE_MISMATCH", 1,
                "LOW_STYLE_SCORE", 3,
                "MISSING_REQUIRED_EVIDENCE", 4
        ));
        assertThat(internalTokenHeader.get()).isEqualTo("test-internal-token");
        assertThat(response.productRefs())
                .extracting("spuId", "skuId", "reason")
                .containsExactly(org.assertj.core.api.Assertions.tuple(
                        1001L,
                        2001L,
                        "fits the requested commute style"
                ));
        JsonNode requestJson = new ObjectMapper().readTree(requestBody.get());
        assertThat(fieldNames(requestJson)).isEqualTo(Set.of(
                "request_id", "session_id", "thread_id", "query", "chat_history", "user_context",
                "candidates", "demand_intent", "debug"
        ));
        JsonNode demandIntent = requestJson.path("demand_intent");
        assertThat(fieldNames(demandIntent)).isEqualTo(Set.of(
                "version", "requestType", "requestedCapabilities", "hardFilters", "softPreferences",
                "subjectMeasurements"
        ));
        assertThat(demandIntent.has("rawQuery")).isFalse();
        JsonNode hardFilters = demandIntent.path("hardFilters");
        assertThat(hardFilters.size()).isEqualTo(1);
        JsonNode seasonFilter = hardFilters.get(0);
        assertThat(fieldNames(seasonFilter)).isEqualTo(Set.of(
                "id", "field", "operator", "values", "strength", "origin", "originTurnId",
                "derivedFromConstraintId", "scope", "weight"
        ));
        assertThat(seasonFilter.path("field").asText()).isEqualTo("season");
        assertThat(seasonFilter.path("values").size()).isEqualTo(1);
        assertThat(seasonFilter.path("values").get(0).asText()).isEqualTo("SUMMER");
        assertThat(seasonFilter.path("strength").asText()).isEqualTo("HARD");
        assertThat(requestJson.path("request_id").asText()).isEqualTo("req-client-test");
        assertThat(requestJson.path("session_id").asText()).isEqualTo("th_client_001");
        assertThat(requestJson.path("thread_id").asText()).isEqualTo("th_client_001");
        assertThat(requestJson.path("query").asText()).isEqualTo("hello");
        assertThat(requestJson.path("chat_history").get(0).path("user_query").asText())
                .isEqualTo("上一轮的问题");
        assertThat(requestJson.path("chat_history").get(0).path("assistant_answer").asText())
                .isEqualTo("上一轮的回答");
        assertThat(requestJson.path("user_context").path("height_cm").decimalValue())
                .isEqualByComparingTo("175.5");
        assertThat(requestJson.path("user_context").path("preferred_styles").get(0).asText())
                .isEqualTo("commute");
        assertThat(requestJson.path("user_context").path("budget_max").decimalValue())
                .isEqualByComparingTo("800.0");
        JsonNode candidate = requestJson.path("candidates").get(0);
        assertThat(candidate.path("category").asText()).isEqualTo("外套");
        assertThat(candidate.path("spu_id").asLong()).isEqualTo(123L);
        assertThat(candidate.path("sku_id").asLong()).isEqualTo(456L);
        assertThat(candidate.path("sale_price").decimalValue()).isEqualByComparingTo("299.0");
        assertThat(candidate.path("stock_status").asText()).isEqualTo("in_stock");
        assertThat(candidate.path("season").get(0).asText()).isEqualTo("autumn");
    }

    @Test
    void streamsPythonTokensAndDoneResponseFromStreamEndpoint() throws Exception {
        AtomicReference<String> acceptHeader = new AtomicReference<>();
        AtomicReference<String> internalTokenHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/stream", exchange -> {
            acceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            internalTokenHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    event: token
                    data: {"content":"我建议"}

                    event: done
                    data: {"request_id":"req-stream-test","answer":"我建议您穿 L 码。","intent":"size_recommendation","product_refs":[{"spu_id":1001,"sku_id":2001,"reason":"尺码匹配","rank_score":0.93}],"rejected_reasons":{"HARD_FILTER_MISMATCH":2,"SIZE_MISMATCH":1,"LOW_STYLE_SCORE":3,"MISSING_REQUIRED_EVIDENCE":4}}

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        RestPythonAssistantClient client = new RestPythonAssistantClient(
                baseUrl,
                1000,
                5000,
                "test-internal-token"
        );
        CapturingStreamHandler handler = new CapturingStreamHandler();

        client.streamChat(minimalPythonRequest(), handler);

        assertThat(acceptHeader.get()).isEqualTo("text/event-stream");
        assertThat(internalTokenHeader.get()).isEqualTo("test-internal-token");
        assertThat(requestBody.get())
                .contains("\"request_id\":\"req-stream-test\"")
                .contains("\"query\":\"hello\"");
        assertThat(handler.tokens).containsExactly("我建议");
        assertThat(handler.done).isNotNull();
        assertThat(handler.done.answer()).isEqualTo("我建议您穿 L 码。");
        assertThat(handler.done.productRefs())
                .extracting("spuId", "skuId", "reason")
                .containsExactly(org.assertj.core.api.Assertions.tuple(1001L, 2001L, "尺码匹配"));
        assertThat(handler.done.rejectedReasons()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "HARD_FILTER_MISMATCH", 2,
                "SIZE_MISMATCH", 1,
                "LOW_STYLE_SCORE", 3,
                "MISSING_REQUIRED_EVIDENCE", 4
        ));
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

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
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
