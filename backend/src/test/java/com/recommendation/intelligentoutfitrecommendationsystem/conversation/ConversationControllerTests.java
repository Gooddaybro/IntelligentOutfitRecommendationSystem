package com.recommendation.intelligentoutfitrecommendationsystem.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ConversationControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(6000);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsConversationListWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsListsReadsMessagesAndArchivesCurrentUserConversation() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        String createdBody = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "秋季外套"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threadId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("秋季外套"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String threadId = objectMapper.readTree(createdBody).path("data").path("threadId").asText();

        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].threadId", hasItem(threadId)));

        mockMvc.perform(get("/api/conversations/{threadId}/messages", threadId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(delete("/api/conversations/{threadId}", threadId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].threadId", not(hasItem(threadId))));
    }

    @Test
    void doesNotExposeOtherUsersConversationMessages() throws Exception {
        String ownerToken = registerAndLogin(nextUsername());
        String otherToken = registerAndLogin(nextUsername());

        String createdBody = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "私有会话"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String threadId = objectMapper.readTree(createdBody).path("data").path("threadId").asText();

        mockMvc.perform(get("/api/conversations/{threadId}/messages", threadId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
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
        return "conversation_user_" + USER_SEQUENCE.incrementAndGet();
    }
}
