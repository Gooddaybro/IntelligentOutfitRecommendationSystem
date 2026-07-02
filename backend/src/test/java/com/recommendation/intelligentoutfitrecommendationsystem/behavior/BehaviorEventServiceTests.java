package com.recommendation.intelligentoutfitrecommendationsystem.behavior;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorEventRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.BehaviorMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehaviorEventServiceTests {

    @Mock
    private BehaviorMapper behaviorMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BehaviorEventService service;

    @BeforeEach
    void setUp() {
        service = new BehaviorEventService(behaviorMapper);
    }

    @Test
    void frontendCannotRecordBackendOnlyEvents() {
        BehaviorEventRequest request = new BehaviorEventRequest(
                "evt_payment_001",
                "PAYMENT_SUCCESS",
                null,
                1002L,
                2101L,
                "thread-1",
                "request-1",
                null,
                1,
                Map.of()
        );

        assertThatThrownBy(() -> service.recordRecommendationInteraction(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("eventType is not allowed for frontend behavior events: PAYMENT_SUCCESS");
        verifyNoInteractions(behaviorMapper);
    }

    @Test
    void duplicateFrontendEventReturnsSuccessAndKeepsEventShape() throws Exception {
        when(behaviorMapper.insert(any(BehaviorEvent.class))).thenReturn(0);
        BehaviorEventRequest request = new BehaviorEventRequest(
                "evt_click_001",
                "RECOMMENDATION_CLICKED",
                null,
                1002L,
                2101L,
                "thread-1",
                "request-1",
                null,
                1,
                Map.of("surface", "assistant_chat", "position", 1)
        );

        var response = service.recordRecommendationInteraction(10L, request);

        assertThat(response.eventId()).isEqualTo("evt_click_001");
        ArgumentCaptor<BehaviorEvent> eventCaptor = ArgumentCaptor.forClass(BehaviorEvent.class);
        verify(behaviorMapper).insert(eventCaptor.capture());
        BehaviorEvent event = eventCaptor.getValue();
        assertThat(event.getEventId()).isEqualTo("evt_click_001");
        assertThat(event.getUserId()).isEqualTo(10L);
        assertThat(event.getEventType()).isEqualTo("RECOMMENDATION_CLICKED");
        assertThat(event.getSource()).isEqualTo("ASSISTANT_RECOMMENDATION");
        assertThat(event.getSpuId()).isEqualTo(1002L);
        assertThat(event.getSkuId()).isEqualTo(2101L);
        assertThat(event.getThreadId()).isEqualTo("thread-1");
        assertThat(event.getRequestId()).isEqualTo("request-1");
        assertThat(event.getQuantity()).isEqualTo(1);
        assertThat(event.getEventTime()).isNotNull();

        JsonNode metadata = objectMapper.readTree(event.getMetadataJson());
        assertThat(metadata.path("surface").asText()).isEqualTo("assistant_chat");
        assertThat(metadata.path("position").asInt()).isEqualTo(1);
    }

    @Test
    void frontendEventGeneratesEventIdWhenMissing() {
        when(behaviorMapper.insert(any(BehaviorEvent.class))).thenReturn(1);
        BehaviorEventRequest request = new BehaviorEventRequest(
                null,
                "RECOMMENDATION_EXPOSED",
                null,
                1002L,
                null,
                "thread-1",
                "request-1",
                null,
                null,
                Map.of()
        );

        var response = service.recordRecommendationInteraction(10L, request);

        assertThat(response.eventId()).isNotBlank();
        ArgumentCaptor<BehaviorEvent> eventCaptor = ArgumentCaptor.forClass(BehaviorEvent.class);
        verify(behaviorMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo(response.eventId());
    }

    @Test
    void businessEventDoesNotLeakPersistenceFailures() {
        when(behaviorMapper.insert(any(BehaviorEvent.class))).thenThrow(new RuntimeException("database is down"));
        BehaviorEventCommand command = new BehaviorEventCommand(
                null,
                10L,
                "CART_ADD",
                null,
                1002L,
                2101L,
                null,
                null,
                null,
                2,
                Map.of("entry", "product_detail")
        );

        assertThatCode(() -> service.recordBusinessEvent(command)).doesNotThrowAnyException();
        verify(behaviorMapper).insert(any(BehaviorEvent.class));
    }
}
