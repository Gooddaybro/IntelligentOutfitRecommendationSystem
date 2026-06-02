package com.recommendation.intelligentoutfitrecommendationsystem.cart.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.cart.model.CartItem;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.model.CartItemView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车数据访问入口。
 *
 * 所有写操作都必须携带 userId，Mapper 层通过 userId 参与 WHERE 条件，保护购物车条目
 * 不被跨用户更新或删除。
 */
@Mapper
public interface CartMapper {

    int existsSkuById(@Param("skuId") Long skuId);

    void insertItem(CartItem item);

    /**
     * 将同一用户的同一 SKU 合并为一条购物车记录。
     *
     * @param userId 当前登录用户 ID，不能作为前端可替换参数暴露
     * @param skuId 商品 SKU ID，必须来自商品目录
     * @param quantity 本次新增数量，必须大于 0
     * @return 受影响行数
     */
    int upsertItem(@Param("userId") Long userId, @Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    CartItem findByUserIdAndSkuId(@Param("userId") Long userId, @Param("skuId") Long skuId);

    List<CartItemView> findItemsByUserId(@Param("userId") Long userId);

    int updateQuantity(@Param("userId") Long userId, @Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    int deleteItem(@Param("userId") Long userId, @Param("skuId") Long skuId);

    int deleteItemsByUserIdAndSkuIds(@Param("userId") Long userId, @Param("skuIds") List<Long> skuIds);

    int clearByUserId(@Param("userId") Long userId);
}
