package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AiTaskControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM ai_task_redrive_audit");
        jdbcTemplate.update("DELETE FROM consumer_inbox");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM ai_task");
    }

    @Test
    void ordinaryUserCannotCreateGlobalRebuildTask() throws Exception {
        mockMvc.perform(post("/api/ai/tasks")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"RAG_REBUILD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCreatesTaskAndOutboxInOneRequest() throws Exception {
        mockMvc.perform(post("/api/ai/tasks")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .header("X-Request-Id", "request-one")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"RAG_REBUILD\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.replayed").value(false));

        Integer taskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_task", Integer.class);
        Integer outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);
        assert taskCount != null;
        assert outboxCount != null;
        org.assertj.core.api.Assertions.assertThat(taskCount).isOne();
        org.assertj.core.api.Assertions.assertThat(outboxCount).isOne();
    }

    @Test
    void repeatedCreateReturnsTheExistingActiveTask() throws Exception {
        String first = mockMvc.perform(post("/api/ai/tasks")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"RAG_REBUILD\"}"))
                .andReturn().getResponse().getContentAsString();

        String taskId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(first).path("data").path("taskId").asText();
        mockMvc.perform(post("/api/ai/tasks")
                        .with(jwt().jwt(token -> token.subject("2"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"RAG_REBUILD\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.replayed").value(true));
    }
}
