package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.api.ProAssistantController;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.ProPythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProDoneEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProPythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProProgressEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantRateLimitService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProAssistantService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRecommendationValidator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRunRegistry;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationAttributionService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProAssistantStreamTests {

    private final ConversationApplicationService conversations = mock(ConversationApplicationService.class);
    private final AssistantRateLimitService limiter = mock(AssistantRateLimitService.class);
    private final ProContextService context = mock(ProContextService.class);
    private final ProRunRegistry registry = mock(ProRunRegistry.class);
    private final ProPythonAssistantClient client = mock(ProPythonAssistantClient.class);
    private final ProRecommendationValidator validator = mock(ProRecommendationValidator.class);
    private final RecommendationAttributionService attribution = mock(RecommendationAttributionService.class);
    private final Executor inlineExecutor = Runnable::run;
    private final ProChatRequest request = new ProChatRequest("th-1", "推荐一件通勤外套", "pro",
            null, null, null, null, null, null, null);
    private final ProRunRegistry.RunCredentials run = new ProRunRegistry.RunCredentials("run-1", "run-secret");
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @BeforeEach
    void setRequestId() {
        MDC.put("requestId", "req-test");
    }

    @AfterEach
    void stopServer() {
        MDC.remove("requestId");
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void clientDropsForeignProgressAndKeepsIncreasingMatchingEvents() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/chat/stream", exchange -> {
            String body = "event: progress\n"
                    + "data: {\"run_id\":\"foreign\",\"sequence\":1,\"tool\":\"search_products\","
                    + "\"stage\":\"started\",\"message\":\"foreign\"}\n\n"
                    + "event: progress\n"
                    + "data: {\"run_id\":\"run-1\",\"sequence\":2,\"tool\":\"search_products\","
                    + "\"stage\":\"started\",\"message\":\"正在搜索商品\"}\n\n"
                    + "event: progress\n"
                    + "data: {\"run_id\":\"run-1\",\"sequence\":1,\"tool\":\"search_products\","
                    + "\"stage\":\"completed\",\"message\":\"旧序号\"}\n\n"
                    + "event: token\n"
                    + "data: {\"content\":\"safe\"}\n\n"
                    + "event: done\n"
                    + "data: " + doneJson() + "\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        List<ProProgressEvent> progress = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        AtomicReference<JsonNode> done = new AtomicReference<>();
        ProPythonAssistantClient transport = new ProPythonAssistantClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), 1000, 2000, "service-secret");
        transport.streamChat(pythonRequest(), new ProPythonAssistantClient.ProStreamHandler() {
            @Override
            public void onProgress(ProProgressEvent event) {
                progress.add(event);
            }

            @Override
            public void onToken(String content) {
                tokens.add(content);
            }

            @Override
            public void onDone(JsonNode result) {
                done.set(result);
            }

            @Override
            public void onError(String code, String message) {
                throw new AssertionError(code + ":" + message);
            }
        });

        assertThat(progress).extracting(ProProgressEvent::sequence).containsExactly(2L);
        assertThat(tokens).containsExactly("safe");
        assertThat(done.get().path("run_id").asText()).isEqualTo("run-1");
    }

    @Test
    void streamPersistsValidatedAnswerAndIgnoresDuplicateDone() throws Exception {
        prepareMocks();
        AtomicReference<ProPythonAssistantClient.ProStreamHandler> callback = new AtomicReference<>();
        doAnswer(invocation -> {
            callback.set(invocation.getArgument(1));
            return null;
        }).when(client).streamChat(any(ProPythonChatRequest.class), any());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProAssistantController(
                service(true))).build();
        MvcResult started = mvc.perform(post("/api/assistant/v2/chat/stream")
                        .principal(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"threadId\":\"th-1\",\"message\":\"推荐一件通勤外套\",\"agentMode\":\"pro\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        callback.get().onProgress(new ProProgressEvent("run-1", 1, "search_products", "started", "正在搜索商品"));
        callback.get().onToken("python draft");
        callback.get().onDone(validatedResult());
        callback.get().onDone(validatedResult());

        String body = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("event:progress", "正在搜索商品", "event:token", "已核验");
        assertThat(body.indexOf("event:token")).isGreaterThan(body.indexOf("正在搜索商品"));
        assertThat(body).contains("event:done").doesNotContain("python draft");
        verify(conversations, times(1)).appendMessage(7L, "th-1", "assistant", "已核验商品。", "req-test");
        verify(attribution, times(1)).record(any());
        verify(registry, times(1)).revoke("run-1", "run-secret");
    }

    @Test
    void cancelledStreamDoesNotPersistPartialAnswer() {
        prepareMocks();
        AtomicReference<ProPythonAssistantClient.ProStreamHandler> callback = new AtomicReference<>();
        doAnswer(invocation -> {
            callback.set(invocation.getArgument(1));
            return null;
        }).when(client).streamChat(any(ProPythonChatRequest.class), any());

        ProAssistantService service = service(true);
        var emitter = service.streamChat(7L, request);
        callback.get().onError("python_stream_interrupted", "cancelled");
        callback.get().onToken("partial answer");
        callback.get().onDone(validatedResult());

        verify(conversations, times(1)).appendMessage(7L, "th-1", "user", request.message(), "req-test");
        verify(conversations, org.mockito.Mockito.never()).appendMessage(
                eq(7L), eq("th-1"), eq("assistant"), anyString(), eq("req-test"));
        verify(attribution, org.mockito.Mockito.never()).record(any());
        verify(registry, times(1)).revoke("run-1", "run-secret");
    }

    @Test
    void synchronousPathPersistsBeforeReturningDone() {
        prepareMocks();
        when(client.chat(any(ProPythonChatRequest.class))).thenReturn(upstreamResult());

        ProDoneEvent result = service(true).chat(7L, request);

        assertThat(result.answer()).isEqualTo("已核验商品。");
        assertThat(result.recommendedSpuIds()).containsExactly(1L);
        verify(attribution).record(any());
        verify(conversations).appendMessage(7L, "th-1", "assistant", "已核验商品。", "req-test");
        verify(registry).revoke("run-1", "run-secret");
    }

    private ProAssistantService service(boolean enabled) {
        return new ProAssistantService(conversations, limiter, context, registry, client, validator,
                attribution, inlineExecutor, 120000L, enabled);
    }

    private void prepareMocks() {
        when(registry.create(eq(7L), eq("th-1"), eq("req-test"))).thenReturn(run);
        when(context.build(eq(7L), eq("th-1"), eq(request), eq("req-test"), eq(run)))
                .thenReturn(pythonRequest());
        when(validator.validate(eq(7L), eq("th-1"), eq("req-test"), eq(run), eq(request), any()))
                .thenReturn(validatedResult());
        when(attribution.record(any())).thenReturn("rec-1");
    }

    private ProPythonChatRequest pythonRequest() {
        return new ProPythonChatRequest("assistant-v2", "pro", "req-test", "run-1", "th-1", request.message(),
                List.of(), Map.of("user_id", 7L), Map.of(), List.of(), "run-secret");
    }

    private JsonNode upstreamResult() {
        return mapper.createObjectNode().put("answer", "python");
    }

    private JsonNode validatedResult() {
        try {
            return mapper.readTree("""
                    {"thread_id":"th-1","run_id":"run-1","agent_mode":"pro",
                     "answer":"已核验商品。","recommended_spu_ids":[1],
                     "recommended_items":[{"spu_id":1,"sku_id":11,"name":"通勤外套",
                       "sale_price":"299.90","main_image_url":"/jacket.png","color":"黑色",
                       "size":"L","available_stock":2,"reason":"符合需求"}],
                     "recommendation_status":"STRONG_MATCH","requirements":[]}
                    """);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private String doneJson() {
        return "{\"contract_version\":\"assistant-v2\",\"agent_mode\":\"pro\","
                + "\"request_id\":\"req-test\",\"run_id\":\"run-1\",\"thread_id\":\"th-1\","
                + "\"answer\":\"answer\",\"product_refs\":[],\"requirements\":[],"
                + "\"stop_reason\":\"needs_input\",\"metrics\":{\"decisions\":1,"
                + "\"tool_calls\":0,\"elapsed_ms\":1,\"token_usage\":null}}";
    }

    private JwtAuthenticationToken user() {
        return new JwtAuthenticationToken(Jwt.withTokenValue("test")
                .header("alg", "none").subject("7").build());
    }
}
