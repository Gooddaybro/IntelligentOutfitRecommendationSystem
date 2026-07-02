package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.service.CartService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.BuyNowRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CancelOrderRequest;
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

    private static final String PAID_STATUS = "PAID";

    private static final String CANCELLED_STATUS = "CANCELLED";

    private static final String CLOSED_STATUS = "CLOSED";

    private static final String DEFAULT_CANCEL_REASON = "USER_CANCELLED";

    private static final int MAX_CLOSE_REASON_LENGTH = 255;

    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderMapper orderMapper;

    private final InventoryMapper inventoryMapper;

    private final CartService cartService;

    private final BehaviorEventService behaviorEventService;

    public OrderService(
            OrderMapper orderMapper,
            InventoryMapper inventoryMapper,
            CartService cartService,
            BehaviorEventService behaviorEventService
    ) {
        this.orderMapper = orderMapper;
        this.inventoryMapper = inventoryMapper;
        this.cartService = cartService;
        this.behaviorEventService = behaviorEventService;
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

        OrderResponse response = createUnpaidOrderFromCheckoutItems(userId, checkoutItems);
        cartService.removePurchasedItems(userId, skuIds);
        return response;
    }

    /**
     * 基于单个 SKU 创建立即购买订单。
     *
     * @param userId 当前认证用户 ID，只能来自服务端 JWT 上下文
     * @param request 前端选择的 SKU 和购买数量，不允许携带价格、金额或用户归属
     * @return 创建后的未支付订单快照
     */
    @Transactional
    public OrderResponse buyNow(Long userId, BuyNowRequest request) {
        validateUserId(userId);
        validateBuyNowRequest(request);
        OrderCheckoutItem checkoutItem = orderMapper.findCheckoutItemBySkuId(request.skuId());
        if (checkoutItem == null) {
            throw new ResourceNotFoundException("sku not found: " + request.skuId());
        }
        checkoutItem.setQuantity(request.quantity());
        return createUnpaidOrderFromCheckoutItems(userId, List.of(checkoutItem));
    }

    private OrderResponse createUnpaidOrderFromCheckoutItems(Long userId, List<OrderCheckoutItem> checkoutItems) {
        validateUserId(userId);
        if (checkoutItems == null || checkoutItems.isEmpty()) {
            throw new BadRequestException("checkout items must not be empty");
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
        recordOrderCreatedEvents(userId, order, orderItems);

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

    /**
     * 取消当前用户自己的未支付订单。
     *
     * @param userId 当前认证用户 ID，只能来自服务端 JWT 上下文
     * @param orderNo 前端持有的订单业务号
     * @param request 可选取消原因，请求体不能决定订单归属或目标状态
     * @return 取消后的订单状态快照
     */
    @Transactional
    public OrderResponse cancelOrder(Long userId, String orderNo, CancelOrderRequest request) {
        validateUserId(userId);
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        SalesOrder order = orderMapper.findOrderByUserIdAndOrderNoForUpdate(userId, normalizedOrderNo);
        if (order == null) {
            throw new ResourceNotFoundException("order not found: " + normalizedOrderNo);
        }
        if (isClosedStatus(order.getStatus())) {
            return toResponse(order, List.of());
        }
        if (PAID_STATUS.equals(order.getStatus())) {
            throw new BadRequestException("paid order cannot be cancelled in this phase");
        }
        validateUnpaid(order);

        String closeReason = normalizeCloseReason(request);
        releaseLockedStock(order);
        closeOrder(order, CANCELLED_STATUS, closeReason);
        return toResponse(order, List.of());
    }

    /**
     * 系统超时任务关闭未支付订单。
     *
     * 该入口不接收用户上下文，因此必须先锁定订单行并重查状态；如果用户已经支付或取消，
     * 本方法直接跳过，避免重复释放库存。
     *
     * @param orderNo 超时扫描得到的订单业务号
     * @param closeReason 系统关闭原因
     */
    @Transactional
    public void closeExpiredOrder(String orderNo, String closeReason) {
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        SalesOrder order = orderMapper.findOrderByOrderNoForUpdate(normalizedOrderNo);
        if (order == null || !UNPAID_STATUS.equals(order.getStatus())) {
            return;
        }
        releaseLockedStock(order);
        closeOrder(order, CLOSED_STATUS, closeReason);
    }

    public List<String> findExpiredUnpaidOrderNos(int unpaidTimeoutMinutes, int batchSize) {
        if (unpaidTimeoutMinutes <= 0) {
            throw new BadRequestException("unpaid timeout minutes must be positive");
        }
        if (batchSize <= 0) {
            throw new BadRequestException("timeout close batch size must be positive");
        }
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(unpaidTimeoutMinutes);
        return orderMapper.findExpiredUnpaidOrderNos(cutoffTime, batchSize);
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

    private void validateBuyNowRequest(BuyNowRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (request.skuId() == null || request.skuId() <= 0) {
            throw new BadRequestException("skuId must be positive");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BadRequestException("quantity must be positive");
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

    private void releaseLockedStock(SalesOrder order) {
        for (OrderItem item : orderMapper.findItemsByOrderId(order.getId())) {
            int affectedRows = inventoryMapper.releaseLockedStock(item.getSkuId(), item.getQuantity());
            if (affectedRows == 0) {
                throw new BadRequestException("locked stock is inconsistent for sku: " + item.getSkuId());
            }
        }
    }

    private void closeOrder(SalesOrder order, String status, String closeReason) {
        int affectedRows = orderMapper.updateOrderClosed(order.getId(), status, closeReason);
        if (affectedRows == 0) {
            throw new BadRequestException("order status changed before close: " + order.getOrderNo());
        }
        order.setStatus(status);
        order.setClosedAt(LocalDateTime.now());
        order.setCloseReason(closeReason);
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

    private void recordOrderCreatedEvents(Long userId, SalesOrder order, List<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            behaviorEventService.recordBusinessEvent(new BehaviorEventCommand(
                    "order:created:" + order.getOrderNo() + ":" + item.getSkuId(),
                    userId,
                    "ORDER_CREATED",
                    null,
                    item.getSpuId(),
                    item.getSkuId(),
                    null,
                    null,
                    order.getOrderNo(),
                    item.getQuantity(),
                    null
            ));
        }
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

    private String normalizeOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BadRequestException("orderNo must not be blank");
        }
        return orderNo.trim();
    }

    private String normalizeCloseReason(CancelOrderRequest request) {
        String reason = request == null ? null : request.reason();
        String normalized = reason == null || reason.isBlank() ? DEFAULT_CANCEL_REASON : reason.trim();
        if (normalized.length() > MAX_CLOSE_REASON_LENGTH) {
            throw new BadRequestException("close reason must not exceed 255 characters");
        }
        return normalized;
    }

    private boolean isClosedStatus(String status) {
        return CANCELLED_STATUS.equals(status) || CLOSED_STATUS.equals(status);
    }

    private void validateUnpaid(SalesOrder order) {
        if (!UNPAID_STATUS.equals(order.getStatus())) {
            throw new BadRequestException("order is not unpaid: " + order.getOrderNo());
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }
}
