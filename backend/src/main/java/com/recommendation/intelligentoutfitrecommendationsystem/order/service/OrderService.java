package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.address.dto.AddressResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.address.service.AddressService;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.service.CartService;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutCalculatedItem;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutCalculation;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.service.CheckoutCalculator;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.BuyNowRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CancelOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CreateOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderItemResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.BuyNowCheckoutItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderAddressSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderOperation;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private final AddressService addressService;

    private final CheckoutCalculator checkoutCalculator;

    private final InventoryApplicationService inventoryApplicationService;

    private final CartService cartService;

    private final BehaviorEventService behaviorEventService;

    private final OrderIdempotencyCoordinator idempotencyCoordinator;

    private final OrderRequestFingerprint requestFingerprint;
    private final ApplicationMetrics metrics;

    public OrderService(
            OrderMapper orderMapper,
            AddressService addressService,
            CheckoutCalculator checkoutCalculator,
            InventoryApplicationService inventoryApplicationService,
            CartService cartService,
            BehaviorEventService behaviorEventService,
            OrderIdempotencyCoordinator idempotencyCoordinator,
            OrderRequestFingerprint requestFingerprint,
            ApplicationMetrics metrics
    ) {
        this.orderMapper = orderMapper;
        this.addressService = addressService;
        this.checkoutCalculator = checkoutCalculator;
        this.inventoryApplicationService = inventoryApplicationService;
        this.cartService = cartService;
        this.behaviorEventService = behaviorEventService;
        this.idempotencyCoordinator = idempotencyCoordinator;
        this.requestFingerprint = requestFingerprint;
        this.metrics = metrics;
    }

    /**
     * 从当前用户购物车创建待支付订单。
     *
     * @param userId 当前认证用户 ID，只能来自服务端 JWT 上下文
     * @param request 前端选择的购物车 SKU 集合和收货地址 ID，不能携带价格、数量、金额或 userId
     * @return 创建后的订单快照
     */
    public IdempotentOrderResult createOrder(
            Long userId,
            String idempotencyKey,
            CreateOrderRequest request
    ) {
        try {
            validateUserId(userId);
            validateRequest(request);
            List<Long> skuIds = normalizeSkuIds(request.skuIds());
            String fingerprint = requestFingerprint.cart(skuIds, request.addressId());
            IdempotentOrderResult result = idempotencyCoordinator.execute(
                    userId,
                    OrderOperation.CART_CHECKOUT,
                    idempotencyKey,
                    fingerprint,
                    () -> createOrderFromCart(userId, skuIds, request.addressId()),
                    orderId -> loadOrderForReplay(userId, orderId)
            );
            metrics.recordOrderCreation("cart", result.replayed() ? "replayed" : "created");
            return result;
        } catch (RuntimeException exception) {
            metrics.recordOrderCreation("cart", "failed");
            throw exception;
        }
    }

    /**
     * 基于单个 SKU 创建立即购买订单。
     *
     * @param userId 当前认证用户 ID，只能来自服务端 JWT 上下文
     * @param request 前端选择的 SKU 和购买数量，不允许携带价格、金额或用户归属
     * @return 创建后的未支付订单快照
     */
    public IdempotentOrderResult buyNow(
            Long userId,
            String idempotencyKey,
            BuyNowRequest request
    ) {
        try {
            validateUserId(userId);
            validateBuyNowRequest(request);
            String fingerprint = requestFingerprint.buyNow(request.skuId(), request.quantity());
            IdempotentOrderResult result = idempotencyCoordinator.execute(
                    userId,
                    OrderOperation.BUY_NOW,
                    idempotencyKey,
                    fingerprint,
                    () -> createOrderFromSku(userId, request),
                    orderId -> loadOrderForReplay(userId, orderId)
            );
            metrics.recordOrderCreation("buy_now", result.replayed() ? "replayed" : "created");
            return result;
        } catch (RuntimeException exception) {
            metrics.recordOrderCreation("buy_now", "failed");
            throw exception;
        }
    }

    /**
     * 在幂等协调器开启的事务内，把可信结算结果固化为购物车订单。
     *
     * 地址必须先读取，随后重新结算并锁定全部库存；只有订单快照完整写入后才清理购物车，
     * 任一步骤抛出异常都由外层事务连同幂等占位一起回滚。
     *
     * @param userId 当前认证用户 ID
     * @param skuIds 已规范化的购物车 SKU 集合
     * @param addressId 当前用户选择的地址簿 ID
     * @return 已持久化订单及其首次响应快照
     */
    private OrderCreationResult createOrderFromCart(Long userId, List<Long> skuIds, Long addressId) {
        AddressResponse address = addressService.requireOwnedAddress(userId, addressId);
        CheckoutCalculation calculation = checkoutCalculator.calculateForOrder(userId, skuIds, addressId);
        List<OrderItem> orderItems = calculation.items().stream()
                .map(this::toOrderItem)
                .toList();
        calculation.items().forEach(item -> inventoryApplicationService.lock(item.skuId(), item.quantity()));
        OrderAddressSnapshot snapshot = toAddressSnapshot(address);
        OrderCreationResult creation = createUnpaidOrder(
                userId,
                orderItems,
                calculation.payableAmount(),
                null,
                snapshot
        );
        cartService.removePurchasedItems(userId, skuIds);
        return creation;
    }

    /**
     * 在幂等协调器事务内创建立即购买订单。
     *
     * 此路径不属于购物车可信结算改造：它保留专用商品事实投影，并继续通过既有库存边界原子锁定库存。
     *
     * @param userId 当前认证用户 ID
     * @param request SKU、数量和可选推荐归因
     * @return 已持久化订单及其首次响应快照
     */
    private OrderCreationResult createOrderFromSku(Long userId, BuyNowRequest request) {
        BuyNowCheckoutItem checkoutItem = orderMapper.findCheckoutItemBySkuId(request.skuId());
        if (checkoutItem == null) {
            throw new ResourceNotFoundException("sku not found: " + request.skuId());
        }
        checkoutItem.setQuantity(request.quantity());
        validateCheckoutItem(checkoutItem);
        BigDecimal lineAmount = checkoutItem.getSalePrice().multiply(BigDecimal.valueOf(checkoutItem.getQuantity()));
        inventoryApplicationService.lock(checkoutItem.getSkuId(), checkoutItem.getQuantity());
        return createUnpaidOrder(
                userId,
                List.of(toOrderItem(checkoutItem, lineAmount)),
                lineAmount,
                request.recommendationId(),
                null
        );
    }

    /**
     * 持久化已完成服务端计价和库存锁定的未支付订单。
     *
     * 调用方必须处于幂等协调器事务中；本方法按订单、明细、可选地址快照和行为事件的顺序写入，
     * 不在内部开启新事务，从而让后续购物车清理和幂等结果链接共享同一次提交。
     *
     * @param userId 当前认证用户 ID
     * @param orderItems 已按服务端事实生成的订单明细
     * @param totalAmount 可信结算金额或立即购买的服务端重算金额
     * @param recommendationId 立即购买的可选推荐归因；购物车路径传 null 并由事件服务回溯归因
     * @param address 购物车订单地址快照；当前立即购买路径传 null
     * @return 已持久化订单及其首次响应快照
     */
    private OrderCreationResult createUnpaidOrder(
            Long userId,
            List<OrderItem> orderItems,
            BigDecimal totalAmount,
            String recommendationId,
            OrderAddressSnapshot address
    ) {
        validateUserId(userId);
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BadRequestException("checkout items must not be empty");
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
        if (address != null) {
            orderMapper.insertAddressSnapshot(order.getId(), address);
        }
        recordOrderCreatedEvents(userId, order, orderItems, recommendationId);

        return new OrderCreationResult(order.getId(), toResponse(order, orderItems, address));
    }

    private OrderResponse loadOrderForReplay(Long userId, Long orderId) {
        SalesOrder order = orderMapper.findOrderByUserIdAndId(userId, orderId);
        if (order == null) {
            throw new ResourceNotFoundException("idempotent order not found");
        }
        return toDetailResponse(order);
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
        return toDetailResponse(order);
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

    private void validateCheckoutItem(BuyNowCheckoutItem item) {
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new BadRequestException("buy-now quantity must be positive for sku: " + item.getSkuId());
        }
        if (!"on_sale".equals(item.getSkuStatus()) || !"on_sale".equals(item.getSpuStatus())) {
            throw new BadRequestException("sku is not available for checkout: " + item.getSkuId());
        }
    }

    private void releaseLockedStock(SalesOrder order) {
        for (OrderItem item : orderMapper.findItemsByOrderId(order.getId())) {
            inventoryApplicationService.release(item.getSkuId(), item.getQuantity());
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

    private OrderItem toOrderItem(BuyNowCheckoutItem checkoutItem, BigDecimal lineAmount) {
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

    private OrderItem toOrderItem(CheckoutCalculatedItem checkoutItem) {
        OrderItem item = new OrderItem();
        item.setSkuId(checkoutItem.skuId());
        item.setSpuId(checkoutItem.spuId());
        item.setSkuCode(checkoutItem.skuCode());
        item.setSpuCode(checkoutItem.spuCode());
        item.setProductName(checkoutItem.name());
        item.setCategoryName(checkoutItem.categoryName());
        item.setColor(checkoutItem.color());
        item.setSize(checkoutItem.size());
        item.setSalePrice(checkoutItem.salePrice());
        item.setQuantity(checkoutItem.quantity());
        item.setLineAmount(checkoutItem.lineAmount());
        item.setMainImageUrl(checkoutItem.mainImageUrl());
        return item;
    }

    private OrderAddressSnapshot toAddressSnapshot(AddressResponse address) {
        return new OrderAddressSnapshot(
                address.id(),
                address.recipientName(),
                address.phone(),
                address.province(),
                address.city(),
                address.district(),
                address.detail()
        );
    }

    private void recordOrderCreatedEvents(
            Long userId,
            SalesOrder order,
            List<OrderItem> orderItems,
            String recommendationId
    ) {
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
                    null,
                    recommendationId
            ));
        }
    }

    private OrderResponse toResponse(SalesOrder order, List<OrderItem> items) {
        return toResponse(order, items, null);
    }

    /**
     * 在订单归属已校验后组装商品和收货地址快照。
     *
     * 列表流程不调用该方法，避免为每个订单额外读取详情快照。
     */
    private OrderResponse toDetailResponse(SalesOrder order) {
        return toResponse(
                order,
                orderMapper.findItemsByOrderId(order.getId()),
                orderMapper.findAddressSnapshotByOrderId(order.getId())
        );
    }

    private OrderResponse toResponse(
            SalesOrder order,
            List<OrderItem> items,
            OrderAddressSnapshot address
    ) {
        return new OrderResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                items.stream().map(this::toItemResponse).toList(),
                address,
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
