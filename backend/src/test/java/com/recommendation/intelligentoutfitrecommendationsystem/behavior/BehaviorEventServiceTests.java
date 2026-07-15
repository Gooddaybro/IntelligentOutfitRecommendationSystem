package com.recommendation.intelligentoutfitrecommendationsystem.behavior;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorEventRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.BehaviorMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.RecommendationAttributionMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
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

    @Mock
    private RecommendationAttributionMapper recommendationAttributionMapper;

    @Mock
    private ApplicationMetrics applicationMetrics;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BehaviorEventService service;

    @BeforeEach
    void setUp() {
        service = new BehaviorEventService(
                behaviorMapper,
                recommendationAttributionMapper,
                applicationMetrics
        );
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

    @Test
    void frontendInteractionPersistsOnlyOwnedSelectedRecommendationItem() {
        when(behaviorMapper.insert(any(BehaviorEvent.class))).thenReturn(1);
        when(recommendationAttributionMapper.existsOwnedSelectedItem(
                "rec_owned", 10L, 1002L, 2101L)).thenReturn(1);
        BehaviorEventRequest request = new BehaviorEventRequest(
                "evt_owned_click",
                "RECOMMENDATION_CLICKED",
                null,
                1002L,
                2101L,
                "thread-1",
                "request-1",
                null,
                1,
                Map.of(),
                "rec_owned"
        );

        service.recordRecommendationInteraction(10L, request);

        ArgumentCaptor<BehaviorEvent> eventCaptor = ArgumentCaptor.forClass(BehaviorEvent.class);
        verify(behaviorMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getRecommendationId()).isEqualTo("rec_owned");
        verify(applicationMetrics).recordRecommendationFunnel("click");
    }

    @Test
    void frontendInteractionRejectsRecommendationOutsideOwnedSelectedItems() {
        when(recommendationAttributionMapper.existsOwnedSelectedItem(
                "rec_forged", 10L, 1002L, 2101L)).thenReturn(0);
        BehaviorEventRequest request = new BehaviorEventRequest(
                "evt_forged_click",
                "RECOMMENDATION_CLICKED",
                null,
                1002L,
                2101L,
                "thread-1",
                "request-1",
                null,
                1,
                Map.of(),
                "rec_forged"
        );

        assertThatThrownBy(() -> service.recordRecommendationInteraction(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("recommendation item is not available for current user");
        verifyNoInteractions(behaviorMapper);
    }

    @Test
    void frontendFavoriteCanAttributeBySelectedSpuWhenSkuIsAbsent() {
        when(behaviorMapper.insert(any(BehaviorEvent.class))).thenReturn(1);
        when(recommendationAttributionMapper.existsOwnedSelectedItem(
                "rec_favorite", 10L, 1002L, null)).thenReturn(1);
        BehaviorEventRequest request = new BehaviorEventRequest(
                "evt_favorite",
                "RECOMMENDATION_FAVORITE_ADD",
                null,
                1002L,
                null,
                "thread-1",
                "request-1",
                null,
                1,
                Map.of(),
                "rec_favorite"
        );

        service.recordRecommendationInteraction(10L, request);

        verify(applicationMetrics).recordRecommendationFunnel("favorite");
    }

    @Test
    void orderAndPaymentEventsInheritRecommendationFromPreviousBusinessStage() {
        when(behaviorMapper.insert(any(BehaviorEvent.class))).thenReturn(1);
        when(recommendationAttributionMapper.findLatestCartRecommendation(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(2101L),
                any()
        )).thenReturn("rec_cart");
        when(recommendationAttributionMapper.findOrderRecommendation(10L, "ORD-1", 2101L))
                .thenReturn("rec_cart");

        service.recordBusinessEvent(new BehaviorEventCommand(
                "evt_order", 10L, "ORDER_CREATED", null, 1002L, 2101L,
                null, null, "ORD-1", 1, Map.of(), null));
        service.recordBusinessEvent(new BehaviorEventCommand(
                "evt_payment", 10L, "PAYMENT_SUCCESS", null, 1002L, 2101L,
                null, null, "ORD-1", 1, Map.of(), null));

        ArgumentCaptor<BehaviorEvent> eventCaptor = ArgumentCaptor.forClass(BehaviorEvent.class);
        verify(behaviorMapper, org.mockito.Mockito.times(2)).insert(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(BehaviorEvent::getRecommendationId)
                .containsExactly("rec_cart", "rec_cart");
        verify(applicationMetrics).recordRecommendationFunnel("order");
        verify(applicationMetrics).recordRecommendationFunnel("payment");
    }
}
