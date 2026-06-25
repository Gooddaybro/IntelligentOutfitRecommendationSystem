package com.recommendation.intelligentoutfitrecommendationsystem.favorite;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTests {

    @Mock
    private FavoriteMapper favoriteMapper;

    @InjectMocks
    private FavoriteService service;

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
        when(favoriteMapper.selectByUserId(10L)).thenReturn(List.of(remaining));

        List<UserFavorite> favorites = service.deleteFavorite(10L, 1001L);

        verify(favoriteMapper).deleteByUserIdAndSpuId(10L, 1001L);
        assertThat(favorites).containsExactly(remaining);
    }
}
