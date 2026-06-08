package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
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
@SpringBootTest
@AutoConfigureMockMvc
class AssistantControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(7000);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                                  "category": "outerwear",
                                  "style": "commute",
                                  "season": "autumn",
                                  "fit": "regular",
                                  "budgetMax": 800
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threadId").isNotEmpty())
                .andExpect(jsonPath("$.data.answer").value("A structured jacket is a good match."))
                .andExpect(jsonPath("$.data.recommendedSpuIds", contains(1001)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String threadId = objectMapper.readTree(chatBody).path("data").path("threadId").asText();

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
                                  "category": "outerwear",
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
                .getContentAsString();

        assertThat(streamBody)
                .contains("event:meta")
                .contains("event:token")
                .contains("A structured")
                .contains("event:done")
                .contains("A structured jacket is a good match.");
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
            return request -> new PythonChatResponse(
                    request.requestId(),
                    "A structured jacket is a good match.",
                    "recommendation",
                    List.of(new PythonProductRef(1001L, 2001L, "fits the requested commute style", null))
            );
        }

        @Bean
        @Primary
        PythonAssistantStreamClient pythonAssistantStreamClient() {
            return (request, handler) -> {
                handler.onToken("A structured");
                handler.onToken(" jacket");
                handler.onDone(new PythonChatResponse(
                        request.requestId(),
                        "A structured jacket is a good match.",
                        "recommendation",
                        List.of(new PythonProductRef(1001L, 2001L, "fits the requested commute style", null))
                ));
            };
        }
    }
}
