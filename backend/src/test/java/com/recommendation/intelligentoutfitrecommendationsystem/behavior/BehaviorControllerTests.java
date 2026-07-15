package com.recommendation.intelligentoutfitrecommendationsystem.behavior;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationAttributionService;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationRecordCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class BehaviorControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(9000);
    private static final AtomicInteger EVENT_SEQUENCE = new AtomicInteger(1000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecommendationAttributionService recommendationAttributionService;

    @Autowired
    private BehaviorEventService behaviorEventService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsBehaviorEventWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/behavior/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recommendationClickBody("evt_without_token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggedInUserCanRecordRecommendationClick() throws Exception {
        String accessToken = registerAndLogin(nextUsername());
        String eventId = nextEventId("evt_click_");

        mockMvc.perform(post("/api/behavior/events")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recommendationClickBody(eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value(eventId));
    }

    @Test
    void rejectsBackendOwnedEventsFromFrontendEndpoint() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/behavior/events")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "%s",
                                  "eventType": "PAYMENT_SUCCESS",
                                  "spuId": 1002,
                                  "skuId": 2101
                                }
                                """.formatted(nextEventId("evt_payment_"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("bad_request"))
                .andExpect(jsonPath("$.message")
                        .value("eventType is not allowed for frontend behavior events: PAYMENT_SUCCESS"));
    }

    @Test
    void loggedInUserCanReadOwnBehaviorSummary() throws Exception {
        String accessToken = registerAndLogin(nextUsername());
        String eventId = nextEventId("evt_summary_click_");

        mockMvc.perform(post("/api/behavior/events")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recommendationClickBody(eventId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/behavior-summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentInterestSpuIds[0]").value(1002))
                .andExpect(jsonPath("$.data.preferredCategories[0]").value("外套"))
                .andExpect(jsonPath("$.data.preferredStyles[0]").value("commute"));
    }

    @Test
    void recommendationAttributionFlowsFromCartToOrderAndPayment() throws Exception {
        String username = nextUsername();
        String accessToken = registerAndLogin(username);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM user_account WHERE username = ?", Long.class, username);
        String recommendationId = recommendationAttributionService.record(new RecommendationRecordCommand(
                userId,
                "req-funnel-integration",
                "thread-funnel-integration",
                "sync",
                List.of(new RecommendationRecordCommand.Item(1002L, 2101L, null)),
                List.of(new RecommendationRecordCommand.Item(1002L, 2101L, null))
        ));

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2101,
                                  "quantity": 1,
                                  "recommendationId": "%s"
                                }
                                """.formatted(recommendationId)))
                .andExpect(status().isOk());

        String orderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [2101]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String orderNo = objectMapper.readTree(orderBody).path("data").path("orderNo").asText();

        behaviorEventService.recordBusinessEvent(new BehaviorEventCommand(
                "payment:funnel:" + orderNo,
                userId,
                "PAYMENT_SUCCESS",
                null,
                1002L,
                2101L,
                null,
                null,
                orderNo,
                1,
                Map.of(),
                null
        ));

        assertThat(jdbcTemplate.queryForList(
                "SELECT recommendation_id FROM behavior_event "
                        + "WHERE user_id = ? AND event_type IN ('CART_ADD', 'ORDER_CREATED', 'PAYMENT_SUCCESS') "
                        + "ORDER BY event_time, id",
                String.class,
                userId
        )).contains(recommendationId, recommendationId, recommendationId);
    }

    private String recommendationClickBody(String eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "RECOMMENDATION_CLICKED",
                  "spuId": 1002,
                  "skuId": 2101,
                  "threadId": "thread-1",
                  "requestId": "request-1",
                  "quantity": 1,
                  "metadata": {
                    "surface": "assistant_chat",
                    "position": 1
                  }
                }
                """.formatted(eventId);
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
        return "behavior_user_" + USER_SEQUENCE.incrementAndGet();
    }

    private String nextEventId(String prefix) {
        return prefix + EVENT_SEQUENCE.incrementAndGet();
    }
}
