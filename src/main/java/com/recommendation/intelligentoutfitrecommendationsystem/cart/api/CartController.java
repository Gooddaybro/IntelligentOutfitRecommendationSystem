package com.recommendation.intelligentoutfitrecommendationsystem.cart.api;

import com.recommendation.intelligentoutfitrecommendationsystem.cart.dto.AddCartItemRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.dto.UpdateCartItemRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.model.CartItemView;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.service.CartService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录用户的公开购物车接口。
 *
 * 购物车属于传统电商交易链路的第一步，不暴露 internal API 给 Python AI 服务；
 * AI 只返回推荐商品，用户加购动作必须由前端携带 JWT 主动触发。
 */
@RestController
@RequestMapping("/api/cart/items")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<List<CartItemView>> listItems(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(cartService.listItems(currentUser.userId()));
    }

    @PostMapping
    public ApiResponse<List<CartItemView>> addItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(cartService.addItem(currentUser.userId(), request.skuId(), request.quantity()));
    }

    @PutMapping("/{skuId}")
    public ApiResponse<List<CartItemView>> updateQuantity(
            Authentication authentication,
            @PathVariable Long skuId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(cartService.updateQuantity(currentUser.userId(), skuId, request.quantity()));
    }

    @DeleteMapping("/{skuId}")
    public ApiResponse<List<CartItemView>> deleteItem(Authentication authentication, @PathVariable Long skuId) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(cartService.deleteItem(currentUser.userId(), skuId));
    }

    @DeleteMapping
    public ApiResponse<Void> clearCart(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        cartService.clearCart(currentUser.userId());
        return ApiResponse.ok(null);
    }
}
