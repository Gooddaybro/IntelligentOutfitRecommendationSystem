package com.recommendation.intelligentoutfitrecommendationsystem.behavior;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.RecommendationAttributionMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.RecommendationSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationAttributionService;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationRecordCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationAttributionServiceTests {

    @Mock
    private RecommendationAttributionMapper mapper;

    @Mock
    private ApplicationMetrics applicationMetrics;

    @Test
    void recordsCandidatesAndMarksOnlyTrustedSelections() {
        RecommendationAttributionService service = new RecommendationAttributionService(mapper, applicationMetrics);
        RecommendationRecordCommand command = new RecommendationRecordCommand(
                10L,
                "req-recommendation-1",
                "thread-1",
                "sync",
                List.of(
                        new RecommendationRecordCommand.Item(1001L, 2001L, null),
                        new RecommendationRecordCommand.Item(1002L, 2002L, null)
                ),
                List.of(
                        new RecommendationRecordCommand.Item(1002L, 2002L, new BigDecimal("0.93")),
                        new RecommendationRecordCommand.Item(9999L, 9999L, new BigDecimal("1.00"))
                )
        );

        String recommendationId = service.record(command);

        assertThat(recommendationId).matches("rec_[0-9a-f]{32}");
        ArgumentCaptor<RecommendationSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(mapper).insertRecommendation(snapshotCaptor.capture());
        RecommendationSnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.recommendationId()).isEqualTo(recommendationId);
        assertThat(snapshot.candidateCount()).isEqualTo(2);
        assertThat(snapshot.ruleVersion()).isEqualTo("java-rule-reranker-v1");
        assertThat(snapshot.items())
                .extracting("skuId", "candidatePosition", "selected", "rankPosition", "rankScore")
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(2001L, 1, false, null, null),
                        org.assertj.core.api.Assertions.tuple(2002L, 2, true, 1, new BigDecimal("0.93"))
                );
        verify(mapper).insertItems(recommendationId, snapshot.items());
        verify(applicationMetrics).recordRecommendationFunnel("exposure");
    }

    @Test
    void readsOnlyAnOwnedCandidateSnapshot() {
        RecommendationAttributionService service = new RecommendationAttributionService(mapper, applicationMetrics);
        RecommendationCandidate candidate = new RecommendationCandidate();
        candidate.setSpuId(1001L);
        candidate.setSkuId(2001L);
        when(mapper.existsOwnedRecommendation("rec_owned", 10L)).thenReturn(1);
        when(mapper.findOwnedCandidates("rec_owned", 10L)).thenReturn(List.of(candidate));

        assertThat(service.getCandidateSnapshot(10L, "rec_owned"))
                .extracting(RecommendationCandidate::getSkuId)
                .containsExactly(2001L);

        when(mapper.existsOwnedRecommendation("rec_other", 10L)).thenReturn(0);
        assertThatThrownBy(() -> service.getCandidateSnapshot(10L, "rec_other"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
