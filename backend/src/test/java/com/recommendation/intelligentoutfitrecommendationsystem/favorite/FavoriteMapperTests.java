package com.recommendation.intelligentoutfitrecommendationsystem.favorite;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class FavoriteMapperTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(7000);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Test
    void insertFavoriteIsIdempotentForSameUserAndSpu() {
        Long userId = createUser();
        UserFavorite favorite = favorite(userId, 1002L);

        assertThat(favoriteMapper.insert(favorite)).isEqualTo(1);
        assertThat(favorite.getId()).isNotNull();
        assertThat(favoriteMapper.insert(favorite(userId, 1002L))).isZero();

        assertThat(favoriteMapper.selectByUserId(userId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getUserId()).isEqualTo(userId);
                    assertThat(item.getSpuId()).isEqualTo(1002L);
                    assertThat(item.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void favoriteQueriesStayScopedToUserAndSupportSpuCount() {
        Long ownerId = createUser();
        Long otherUserId = createUser();
        favoriteMapper.insert(favorite(ownerId, 1001L));
        favoriteMapper.insert(favorite(ownerId, 1003L));
        favoriteMapper.insert(favorite(otherUserId, 1003L));

        assertThat(favoriteMapper.selectByUserIdAndSpuId(ownerId, 1003L)).isNotNull();
        assertThat(favoriteMapper.selectByUserId(ownerId))
                .extracting(UserFavorite::getSpuId)
                .containsExactly(1003L, 1001L);
        assertThat(favoriteMapper.countBySpuId(1003L)).isEqualTo(2);

        assertThat(favoriteMapper.deleteByUserIdAndSpuId(ownerId, 1003L)).isEqualTo(1);
        assertThat(favoriteMapper.selectByUserIdAndSpuId(ownerId, 1003L)).isNull();
        assertThat(favoriteMapper.selectByUserIdAndSpuId(otherUserId, 1003L)).isNotNull();
    }

    private UserFavorite favorite(Long userId, Long spuId) {
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setSpuId(spuId);
        return favorite;
    }

    private Long createUser() {
        String username = "favorite_mapper_user_" + USER_SEQUENCE.incrementAndGet();
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash("encoded-password");
        userAccount.setStatus("active");
        userAuthMapper.insertUserAccount(userAccount);

        Long roleId = userAuthMapper.findRoleIdByCode("USER");
        userAuthMapper.insertUserRole(userAccount.getId(), roleId);
        return userAccount.getId();
    }
}
