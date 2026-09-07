package com.recommendation.intelligentoutfitrecommendationsystem.favorite;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.FavoriteProduct;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.service.FavoriteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private FavoriteService service;

    @Test
    void addFavoriteRecordsBehaviorOnlyWhenFavoriteIsNew() {
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(10L);
        favorite.setSpuId(1001L);
        FavoriteProduct favoriteProduct = favoriteProduct(1001L);
        when(favoriteMapper.existsSpuById(1001L)).thenReturn(1);
        when(favoriteMapper.selectByUserIdAndSpuId(10L, 1001L)).thenReturn(null);
        when(favoriteMapper.selectFavoriteProductsByUserId(10L)).thenReturn(List.of(favoriteProduct));

        List<FavoriteProduct> favorites = service.addFavorite(10L, 1001L, null);

        assertThat(favorites).containsExactly(favoriteProduct);
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
        when(favoriteMapper.existsSpuById(1001L)).thenReturn(1);
        when(favoriteMapper.selectByUserIdAndSpuId(10L, 1001L)).thenReturn(existing);
        when(favoriteMapper.selectFavoriteProductsByUserId(10L)).thenReturn(List.of(favoriteProduct(1001L)));

        service.addFavorite(10L, 1001L, null);

        verify(behaviorEventService, never()).recordBusinessEvent(argThat(command ->
                "FAVORITE_ADD".equals(command.eventType())
        ));
    }

    @Test
    void addFavoritePropagatesRecommendationAttribution() {
        when(favoriteMapper.existsSpuById(1001L)).thenReturn(1);
        when(favoriteMapper.selectByUserIdAndSpuId(10L, 1001L)).thenReturn(null);
        when(favoriteMapper.selectFavoriteProductsByUserId(10L)).thenReturn(List.of());

        service.addFavorite(10L, 1001L, "rec_favorite_test");

        verify(behaviorEventService).recordBusinessEvent(argThat(command ->
                "rec_favorite_test".equals(command.recommendationId())
                        && "FAVORITE_ADD".equals(command.eventType())
        ));
    }

    @Test
    void addFavoriteRejectsUnknownSpuBeforeInsert() {
        when(favoriteMapper.existsSpuById(999999L)).thenReturn(0);

        assertThatThrownBy(() -> service.addFavorite(10L, 999999L, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("product not found: 999999");

        verify(favoriteMapper, never()).insert(any());
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
        FavoriteProduct favoriteProduct = favoriteProduct(1002L);
        when(favoriteMapper.selectFavoriteProductsByUserId(10L)).thenReturn(List.of(favoriteProduct));

        List<FavoriteProduct> favorites = service.deleteFavorite(10L, 1001L);

        verify(favoriteMapper).deleteByUserIdAndSpuId(10L, 1001L);
        assertThat(favorites).containsExactly(favoriteProduct);
    }

    private FavoriteProduct favoriteProduct(Long spuId) {
        FavoriteProduct favoriteProduct = new FavoriteProduct();
        favoriteProduct.setSpuId(spuId);
        favoriteProduct.setName("Favorite product");
        return favoriteProduct;
    }
}
