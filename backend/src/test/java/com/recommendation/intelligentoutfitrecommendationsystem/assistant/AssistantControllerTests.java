package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "app.ai.circuit-breaker.enabled=false")
@AutoConfigureMockMvc
class AssistantControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(7000);
    private static final AtomicBoolean FAIL_SYNC_PYTHON = new AtomicBoolean();
    private static final AtomicBoolean FAIL_STREAM_PYTHON = new AtomicBoolean();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void resetFakePythonFailures() {
        FAIL_SYNC_PYTHON.set(false);
        FAIL_STREAM_PYTHON.set(false);
    }

    @Test
    void rejectsAssistantChatWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "recommend a jacket"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAssistantStreamChatWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/assistant/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "message": "recommend a jacket"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatsWithPythonClientAndPersistsConversationMessages() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        String chatBody = mockMvc.perform(post("/api/assistant/chat")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "recommend a jacket for autumn commute",
                                  "category": "外套",
                                  "style": "commute",
                                  "season": "autumn",
                                  "fit": "regular",
                                  "budgetMax": 800
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threadId").isNotEmpty())
                .andExpect(jsonPath("$.data.answer").value("A structured jacket is a good match."))
                .andExpect(jsonPath("$.data.recommendedSpuIds", contains(1002)))
                .andExpect(jsonPath("$.data.recommendedItems[0].spuId").value(1002))
                .andExpect(jsonPath("$.data.recommendedItems[0].skuId").value(2101))
                .andExpect(jsonPath("$.data.recommendedItems[0].reason").value("fits the requested commute style"))
                .andExpect(jsonPath("$.data.recommendationId").value(org.hamcrest.Matchers.startsWith("rec_")))
                .andExpect(jsonPath("$.data.resolvedIntent.category").value("外套"))
                .andExpect(jsonPath("$.data.resolvedIntent.budgetMax").value(800))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String threadId = objectMapper.readTree(chatBody).path("data").path("threadId").asText();
        String recommendationId = objectMapper.readTree(chatBody).path("data").path("recommendationId").asText();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT candidate_count FROM assistant_recommendation WHERE recommendation_id = ?",
                Integer.class,
                recommendationId
        )).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assistant_recommendation_item "
                        + "WHERE recommendation_id = ? AND selected = TRUE",
                Integer.class,
                recommendationId
        )).isEqualTo(1);

        mockMvc.perform(get("/api/conversations/{threadId}/messages", threadId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].content").value("recommend a jacket for autumn commute"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"))
                .andExpect(jsonPath("$.data[1].content").value("A structured jacket is a good match."));
    }

    @Test
    void streamsAssistantChatEventsAndPersistsConversationMessages() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        var mvcResult = mockMvc.perform(post("/api/assistant/chat/stream")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "message": "recommend a jacket for autumn commute",
                                  "category": "外套",
                                  "style": "commute",
                                  "season": "autumn",
                                  "fit": "regular",
                                  "budgetMax": 800
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        String streamBody = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(streamBody)
                .contains("event:meta")
                .contains("event:token")
                .contains("A structured")
                .contains("event:done")
                .contains("A structured jacket is a good match.")
                .contains("\"recommended_spu_ids\":[1002]")
                .contains("\"recommended_items\"")
                .contains("\"resolved_intent\"")
                .contains("\"recommendation_id\":\"rec_")
                .contains("fits the requested commute style")
                .doesNotContain("9999");
    }

    @Test
    void syncChatReturnsSafeFallbackWhenPythonFails() throws Exception {
        FAIL_SYNC_PYTHON.set(true);
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/assistant/chat")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "recommend a jacket for autumn commute",
                                  "category": "外套",
                                  "style": "commute",
                                  "season": "autumn",
                                  "fit": "regular",
                                  "budgetMax": 800
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("AI 导购暂时不可用")))
                .andExpect(jsonPath("$.data.recommendedSpuIds").isEmpty())
                .andExpect(jsonPath("$.data.recommendedItems").isEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/tmp/python-internal"))));
    }

    @Test
    void streamChatReturnsSafeErrorWhenPythonFails() throws Exception {
        FAIL_STREAM_PYTHON.set(true);
        String accessToken = registerAndLogin(nextUsername());

        var mvcResult = mockMvc.perform(post("/api/assistant/chat/stream")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "message": "recommend a jacket for autumn commute",
                                  "category": "外套",
                                  "style": "commute",
                                  "season": "autumn",
                                  "fit": "regular",
                                  "budgetMax": 800
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        String streamBody = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(streamBody)
                .contains("event:meta")
                .contains("event:error")
                .contains("AI 导购暂时不可用")
                .doesNotContain("provider secret")
                .doesNotContain("/tmp/python-internal")
                .doesNotContain("raw_secret");
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "StrongPassword123!",
                                  "email": "%s@example.com"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "StrongPassword123!"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        return data.path("accessToken").asText();
    }

    private String nextUsername() {
        return "assistant_user_" + USER_SEQUENCE.incrementAndGet();
    }

    @TestConfiguration
    static class FakePythonClientConfig {
        @Bean
        @Primary
        PythonAssistantClient pythonAssistantClient() {
            return request -> {
                if (FAIL_SYNC_PYTHON.get()) {
                    throw new ExternalServiceException("provider secret /tmp/python-internal");
                }
                return new PythonChatResponse(
                        request.requestId(),
                        "A structured jacket is a good match.",
                        "recommendation",
                        List.of(
                                new PythonProductRef(9999L, 8888L, "hallucinated product must be ignored", null),
                                new PythonProductRef(1002L, 2101L, "fits the requested commute style", null)
                        )
                );
            };
        }

        @Bean
        @Primary
        PythonAssistantStreamClient pythonAssistantStreamClient() {
            return (request, handler) -> {
                if (FAIL_STREAM_PYTHON.get()) {
                    handler.onError("raw_secret", "provider secret /tmp/python-internal");
                    return;
                }
                handler.onToken("A structured");
                handler.onToken(" jacket");
                handler.onDone(new PythonChatResponse(
                        request.requestId(),
                        "A structured jacket is a good match.",
                        "recommendation",
                        List.of(
                                new PythonProductRef(9999L, 8888L, "hallucinated product must be ignored", null),
                                new PythonProductRef(1002L, 2101L, "fits the requested commute style", null)
                        )
                ));
            };
        }
    }
}
