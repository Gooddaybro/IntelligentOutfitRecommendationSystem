package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import com.recommendation.intelligentoutfitrecommendationsystem.cart.service.CartService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CreateOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderItemResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderCheckoutItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单业务服务。
 *
 * 该服务负责把当前用户购物车意图转换为 UNPAID 订单，并在同一事务中完成价格重算、
 * 库存锁定、订单快照落库和购物车清理，避免前端篡改金额或并发超卖。
 */
@Service
public class OrderService {

    private static final String CART_SOURCE = "CART";

    private static final String UNPAID_STATUS = "UNPAID";

    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderMapper orderMapper;

    private final InventoryMapper inventoryMapper;

    private final CartService cartService;

    public OrderService(OrderMapper orderMapper, InventoryMapper inventoryMapper, CartService cartService) {
        this.orderMapper = orderMapper;
        this.inventoryMapper = inventoryMapper;
        this.cartService = cartService;
    }

    /**
     * 从当前用户购物车创建待支付订单。
     *
     * @param userId 当前认证用户 ID，只能来自服务端 JWT 上下文
     * @param request 前端选择的购物车 SKU 集合，不能携带价格、数量或 userId
     * @return 创建后的订单快照
     */
    @Transactional
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        validateUserId(userId);
        validateRequest(request);
        List<Long> skuIds = normalizeSkuIds(request.skuIds());
        List<OrderCheckoutItem> checkoutItems = orderMapper.findCheckoutItemsFromCart(userId, skuIds);
        if (checkoutItems.size() != skuIds.size()) {
            throw new ResourceNotFoundException("cart item not found");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderCheckoutItem checkoutItem : checkoutItems) {
            validateCheckoutItem(checkoutItem);
            BigDecimal lineAmount = checkoutItem.getSalePrice().multiply(BigDecimal.valueOf(checkoutItem.getQuantity()));
            totalAmount = totalAmount.add(lineAmount);
            lockStock(checkoutItem);
            orderItems.add(toOrderItem(checkoutItem, lineAmount));
        }

        SalesOrder order = new SalesOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(UNPAID_STATUS);
        orderMapper.insertOrder(order);

        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
        }
        orderMapper.insertItems(orderItems);
        cartService.removePurchasedItems(userId, skuIds);

        return toResponse(order, orderItems);
    }

    public List<OrderResponse> listOrders(Long userId) {
        validateUserId(userId);
        return orderMapper.findOrdersByUserId(userId).stream()
                .map(order -> toResponse(order, List.of()))
                .toList();
    }

    public OrderResponse getOrderDetail(Long userId, String orderNo) {
        validateUserId(userId);
        if (orderNo == null || orderNo.isBlank()) {
            throw new BadRequestException("orderNo must not be blank");
        }
        SalesOrder order = orderMapper.findOrderByUserIdAndOrderNo(userId, orderNo);
        if (order == null) {
            throw new ResourceNotFoundException("order not found: " + orderNo);
        }
        return toResponse(order, orderMapper.findItemsByOrderId(order.getId()));
    }

    private void validateRequest(CreateOrderRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (!CART_SOURCE.equals(request.source())) {
            throw new BadRequestException("order source is not supported: " + request.source());
        }
        if (request.skuIds() == null || request.skuIds().isEmpty()) {
            throw new BadRequestException("skuIds must not be empty");
        }
    }

    private List<Long> normalizeSkuIds(List<Long> skuIds) {
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long skuId : skuIds) {
            if (skuId == null || skuId <= 0) {
                throw new BadRequestException("skuIds must be positive");
            }
            normalized.add(skuId);
        }
        return List.copyOf(normalized);
    }

    private void validateCheckoutItem(OrderCheckoutItem item) {
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new BadRequestException("cart quantity must be positive for sku: " + item.getSkuId());
        }
        if (!"on_sale".equals(item.getSkuStatus()) || !"on_sale".equals(item.getSpuStatus())) {
            throw new BadRequestException("sku is not available for checkout: " + item.getSkuId());
        }
    }

    private void lockStock(OrderCheckoutItem item) {
        int affectedRows = inventoryMapper.lockStock(item.getSkuId(), item.getQuantity());
        if (affectedRows == 0) {
            throw new BadRequestException("insufficient stock for sku: " + item.getSkuId());
        }
    }

    private OrderItem toOrderItem(OrderCheckoutItem checkoutItem, BigDecimal lineAmount) {
        OrderItem item = new OrderItem();
        item.setSkuId(checkoutItem.getSkuId());
        item.setSpuId(checkoutItem.getSpuId());
        item.setSkuCode(checkoutItem.getSkuCode());
        item.setSpuCode(checkoutItem.getSpuCode());
        item.setProductName(checkoutItem.getProductName());
        item.setCategoryName(checkoutItem.getCategoryName());
        item.setColor(checkoutItem.getColor());
        item.setSize(checkoutItem.getSize());
        item.setSalePrice(checkoutItem.getSalePrice());
        item.setQuantity(checkoutItem.getQuantity());
        item.setLineAmount(lineAmount);
        item.setMainImageUrl(checkoutItem.getMainImageUrl());
        return item;
    }

    private OrderResponse toResponse(SalesOrder order, List<OrderItem> items) {
        return new OrderResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                items.stream().map(this::toItemResponse).toList(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getClosedAt(),
                order.getCloseReason()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getSkuId(),
                item.getSpuId(),
                item.getSkuCode(),
                item.getSpuCode(),
                item.getProductName(),
                item.getCategoryName(),
                item.getColor(),
                item.getSize(),
                item.getSalePrice(),
                item.getQuantity(),
                item.getLineAmount(),
                item.getMainImageUrl()
        );
    }

    private String generateOrderNo() {
        int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "ORD" + LocalDateTime.now().format(ORDER_TIME_FORMATTER) + suffix;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }
}
