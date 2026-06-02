package com.recommendation.intelligentoutfitrecommendationsystem.cart.service;

import com.recommendation.intelligentoutfitrecommendationsystem.cart.mapper.CartMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.model.CartItemView;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 购物车业务服务。
 *
 * 该服务是公开购物车 API 和 cart_item 表之间的边界，负责把前端传入的用户意图
 * 转换为用户隔离的购物车写操作；库存强一致扣减仍保留给后续订单模块处理。
 */
@Service
public class CartService {

    private static final int MAX_QUANTITY = 99;

    private final CartMapper cartMapper;

    public CartService(CartMapper cartMapper) {
        this.cartMapper = cartMapper;
    }

    public List<CartItemView> listItems(Long userId) {
        validateUserId(userId);
        return cartMapper.findItemsByUserId(userId);
    }

    /**
     * 将一个 SKU 加入当前用户购物车。
     *
     * @param userId 当前认证用户 ID，只能来自服务端认证上下文
     * @param skuId 商品 SKU ID，必须存在于商品目录
     * @param quantity 本次新增数量，必须大于 0
     * @return 写入后的当前购物车列表
     */
    @Transactional
    public List<CartItemView> addItem(Long userId, Long skuId, Integer quantity) {
        validateUserId(userId);
        validateSkuId(skuId);
        validateQuantity(quantity);
        ensureSkuExists(skuId);

        cartMapper.upsertItem(userId, skuId, quantity);
        return cartMapper.findItemsByUserId(userId);
    }

    @Transactional
    public List<CartItemView> updateQuantity(Long userId, Long skuId, Integer quantity) {
        validateUserId(userId);
        validateSkuId(skuId);
        validateQuantity(quantity);

        int affectedRows = cartMapper.updateQuantity(userId, skuId, quantity);
        if (affectedRows == 0) {
            throw new ResourceNotFoundException("cart item not found");
        }
        return cartMapper.findItemsByUserId(userId);
    }

    @Transactional
    public List<CartItemView> deleteItem(Long userId, Long skuId) {
        validateUserId(userId);
        validateSkuId(skuId);

        int affectedRows = cartMapper.deleteItem(userId, skuId);
        if (affectedRows == 0) {
            throw new ResourceNotFoundException("cart item not found");
        }
        return cartMapper.findItemsByUserId(userId);
    }

    @Transactional
    public void clearCart(Long userId) {
        validateUserId(userId);
        cartMapper.clearByUserId(userId);
    }

    private void ensureSkuExists(Long skuId) {
        if (cartMapper.existsSkuById(skuId) == 0) {
            throw new ResourceNotFoundException("sku not found: " + skuId);
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }

    private void validateSkuId(Long skuId) {
        if (skuId == null || skuId <= 0) {
            throw new BadRequestException("skuId must be positive");
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("quantity must be positive");
        }
        if (quantity > MAX_QUANTITY) {
            throw new BadRequestException("quantity must not exceed 99");
        }
    }
}
