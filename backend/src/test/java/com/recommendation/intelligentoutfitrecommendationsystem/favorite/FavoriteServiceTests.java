package com.recommendation.intelligentoutfitrecommendationsystem.favorite;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.service.FavoriteService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTests {

    @Mock
    private FavoriteMapper favoriteMapper;

    @Mock
    private BehaviorEventService behaviorEventService;

    @Mock
    private RecommendationCandidateQueryService recommendationCandidateQueryService;

    @InjectMocks
    private FavoriteService service;

    @Test
    void addFavoriteRecordsBehaviorOnlyWhenFavoriteIsNew() {
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(10L);
        favorite.setSpuId(1001L);
        RecommendationCandidate candidate = candidate(1001L);
        when(favoriteMapper.selectByUserIdAndSpuId(10L, 1001L)).thenReturn(null);
        when(favoriteMapper.selectByUserId(10L)).thenReturn(List.of(favorite));
        when(recommendationCandidateQueryService.findCandidatesBySpuIds(List.of(1001L)))
                .thenReturn(List.of(candidate));

        List<RecommendationCandidate> favorites = service.addFavorite(10L, 1001L);

        assertThat(favorites).containsExactly(candidate);
        verify(behaviorEventService).recordBusinessEvent(argThat(command ->
                "FAVORITE_ADD".equals(command.eventType())
                        && Long.valueOf(10L).equals(command.userId())
                        && Long.valueOf(1001L).equals(command.spuId())
        ));
    }

    @Test
    void addFavoriteDoesNotRecordBehaviorWhenFavoriteAlreadyExists() {
        UserFavorite existing = new UserFavorite();
        existing.setUserId(10L);
        existing.setSpuId(1001L);
        when(favoriteMapper.selectByUserIdAndSpuId(10L, 1001L)).thenReturn(existing);
        when(favoriteMapper.selectByUserId(10L)).thenReturn(List.of(existing));
        when(recommendationCandidateQueryService.findCandidatesBySpuIds(List.of(1001L)))
                .thenReturn(List.of(candidate(1001L)));

        service.addFavorite(10L, 1001L);

        verify(behaviorEventService, never()).recordBusinessEvent(argThat(command ->
                "FAVORITE_ADD".equals(command.eventType())
        ));
    }

    @Test
    void deleteFavoriteRejectsNonPositiveUserId() {
        assertThatThrownBy(() -> service.deleteFavorite(0L, 1001L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("userId must be positive");

        verify(favoriteMapper, never()).deleteByUserIdAndSpuId(0L, 1001L);
    }

    @Test
    void deleteFavoriteRejectsNonPositiveSpuId() {
        assertThatThrownBy(() -> service.deleteFavorite(10L, 0L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("spuId must be positive");

        verify(favoriteMapper, never()).deleteByUserIdAndSpuId(10L, 0L);
    }

    @Test
    void deleteFavoriteIsIdempotentAndReturnsCurrentFavorites() {
        UserFavorite remaining = new UserFavorite();
        remaining.setUserId(10L);
        remaining.setSpuId(1002L);
        RecommendationCandidate candidate = candidate(1002L);
        when(favoriteMapper.selectByUserId(10L)).thenReturn(List.of(remaining));
        when(recommendationCandidateQueryService.findCandidatesBySpuIds(List.of(1002L)))
                .thenReturn(List.of(candidate));

        List<RecommendationCandidate> favorites = service.deleteFavorite(10L, 1001L);

        verify(favoriteMapper).deleteByUserIdAndSpuId(10L, 1001L);
        assertThat(favorites).containsExactly(candidate);
    }

    private RecommendationCandidate candidate(Long spuId) {
        RecommendationCandidate candidate = new RecommendationCandidate();
        candidate.setSpuId(spuId);
        candidate.setName("Favorite product");
        return candidate;
    }
}
