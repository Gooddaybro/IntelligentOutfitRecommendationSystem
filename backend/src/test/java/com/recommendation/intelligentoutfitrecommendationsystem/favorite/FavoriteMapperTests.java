package com.recommendation.intelligentoutfitrecommendationsystem.favorite;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.FavoriteProduct;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void favoriteProjectionUsesPurchasablePriceAndZeroStockForOffSaleSpu() {
        Long userId = createUser();
        List<SkuState> originalSkuStates = skuStates(1001L);
        String originalSpuStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM product_spu WHERE id = ?", String.class, 1001L);
        Long unavailableSkuId = originalSkuStates.get(0).skuId();
        Long purchasableSkuId = originalSkuStates.get(1).skuId();

        try {
            jdbcTemplate.update("UPDATE product_spu SET status = 'on_sale' WHERE id = ?", 1001L);
            for (SkuState sku : originalSkuStates) {
                jdbcTemplate.update("UPDATE product_sku SET status = 'off_sale', sale_price = 30.00 WHERE id = ?",
                        sku.skuId());
                jdbcTemplate.update("UPDATE inventory SET available_stock = 0 WHERE sku_id = ?", sku.skuId());
            }
            jdbcTemplate.update("UPDATE product_sku SET sale_price = 10.00 WHERE id = ?", unavailableSkuId);
            jdbcTemplate.update("UPDATE product_sku SET status = 'on_sale', sale_price = 20.00 WHERE id = ?",
                    purchasableSkuId);
            jdbcTemplate.update("UPDATE inventory SET available_stock = 7 WHERE sku_id = ?", purchasableSkuId);
            favoriteMapper.insert(favorite(userId, 1001L));

            FavoriteProduct purchasableProduct = favoriteMapper.selectFavoriteProductsByUserId(userId).get(0);
            assertThat(purchasableProduct.getSalePrice()).isEqualByComparingTo("20.00");
            assertThat(purchasableProduct.getAvailabilityStatus()).isEqualTo("available");
            assertThat(purchasableProduct.getTotalAvailableStock()).isEqualTo(7);

            jdbcTemplate.update("UPDATE product_spu SET status = 'off_sale' WHERE id = ?", 1001L);

            FavoriteProduct offSaleProduct = favoriteMapper.selectFavoriteProductsByUserId(userId).get(0);
            assertThat(offSaleProduct.getSalePrice()).isEqualByComparingTo("10.00");
            assertThat(offSaleProduct.getAvailabilityStatus()).isEqualTo("unavailable");
            assertThat(offSaleProduct.getTotalAvailableStock()).isZero();
        } finally {
            jdbcTemplate.update("UPDATE product_spu SET status = ? WHERE id = ?", originalSpuStatus, 1001L);
            for (SkuState sku : originalSkuStates) {
                jdbcTemplate.update("UPDATE product_sku SET status = ?, sale_price = ? WHERE id = ?",
                        sku.status(), sku.salePrice(), sku.skuId());
                jdbcTemplate.update("UPDATE inventory SET available_stock = ? WHERE sku_id = ?",
                        sku.availableStock(), sku.skuId());
            }
        }
    }

    private List<SkuState> skuStates(Long spuId) {
        return jdbcTemplate.query("""
                        SELECT s.id, s.status, s.sale_price, i.available_stock
                        FROM product_sku s
                        JOIN inventory i ON i.sku_id = s.id
                        WHERE s.spu_id = ?
                        ORDER BY s.id
                        """,
                (resultSet, rowNum) -> new SkuState(
                        resultSet.getLong("id"),
                        resultSet.getString("status"),
                        resultSet.getBigDecimal("sale_price"),
                        resultSet.getInt("available_stock")),
                spuId);
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

    private record SkuState(Long skuId, String status, BigDecimal salePrice, Integer availableStock) {
    }
}
