package com.recommendation.intelligentoutfitrecommendationsystem.behavior;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.BehaviorMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorProductSignal;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorSummaryService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehaviorSummaryServiceTests {

    @Mock
    private BehaviorMapper behaviorMapper;

    @Test
    void summaryGroupsRecentInterestCartPurchaseAndPreferences() {
        BehaviorSummaryService service = new BehaviorSummaryService(behaviorMapper);
        when(behaviorMapper.findRecentSignals(eq(10L), any(LocalDateTime.class), eq(200)))
                .thenReturn(List.of(
                        signal("RECOMMENDATION_CLICKED", 1001L, "外套", "commute,minimal"),
                        signal("CART_ADD", 1002L, "裤子", "casual"),
                        signal("PAYMENT_SUCCESS", 1003L, "外套", "commute"),
                        signal("RECOMMENDATION_EXPOSED", 1004L, "鞋履", "sport")
                ));

        BehaviorSummaryResponse summary = service.getSummary(10L);

        assertThat(summary.recentInterestSpuIds()).containsExactly(1001L);
        assertThat(summary.recentCartSpuIds()).containsExactly(1002L);
        assertThat(summary.recentPurchasedSpuIds()).containsExactly(1003L);
        assertThat(summary.preferredCategories()).containsExactly("外套", "裤子");
        assertThat(summary.preferredStyles()).containsExactly("commute", "minimal", "casual");
        assertThat(summary.recentExposedSpuIds()).containsExactly(1004L);

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(behaviorMapper).findRecentSignals(eq(10L), sinceCaptor.capture(), eq(200));
        assertThat(sinceCaptor.getValue()).isBefore(LocalDateTime.now().minusDays(29));
        assertThat(sinceCaptor.getValue()).isAfter(LocalDateTime.now().minusDays(31));
    }

    @Test
    void summaryRejectsInvalidUserId() {
        BehaviorSummaryService service = new BehaviorSummaryService(behaviorMapper);

        assertThatThrownBy(() -> service.getSummary(0L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("userId must be positive");
    }

    private BehaviorProductSignal signal(String eventType, Long spuId, String categoryName, String styleTags) {
        BehaviorProductSignal signal = new BehaviorProductSignal();
        signal.setEventType(eventType);
        signal.setSpuId(spuId);
        signal.setSkuId(spuId + 1000);
        signal.setCategoryName(categoryName);
        signal.setStyleTags(styleTags);
        signal.setEventTime(LocalDateTime.now());
        return signal;
    }
}
