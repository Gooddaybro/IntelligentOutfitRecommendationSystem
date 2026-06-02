package com.recommendation.intelligentoutfitrecommendationsystem.order.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CancelOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CreateOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderService;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录用户的公开订单接口。
 *
 * 本阶段订单入口只承接购物车结算，不暴露 internal API 给 Python AI 服务；AI 推荐结束后，
 * 用户仍需要在前端携带 JWT 主动确认下单，避免大模型直接操作交易链路。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(orderService.createOrder(currentUser.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> listOrders(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(orderService.listOrders(currentUser.userId()));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponse> getOrderDetail(Authentication authentication, @PathVariable String orderNo) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(orderService.getOrderDetail(currentUser.userId(), orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            Authentication authentication,
            @PathVariable String orderNo,
            @RequestBody(required = false) CancelOrderRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(orderService.cancelOrder(currentUser.userId(), orderNo, request));
    }
}
