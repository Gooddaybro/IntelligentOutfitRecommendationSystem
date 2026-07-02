package com.recommendation.intelligentoutfitrecommendationsystem.payment.service;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.CreatePaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.MockPaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.PaymentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.mapper.PaymentMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.model.Payment;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.model.PaymentCallbackLog;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.PaymentRequestContext;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.PaymentResult;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.PaymentStrategy;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.PaymentStrategyRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 模拟支付业务服务。
 *
 * 该服务是 mock 支付阶段的事务边界：先锁定订单主表行，再根据订单状态决定首次支付、
 * 幂等返回或拒绝支付，保证库存确认、支付流水和订单状态不会在并发请求下重复执行。
 */
@Service
public class PaymentService {

    private static final String MOCK_CHANNEL = "MOCK";

    private static final String SUCCESS_STATUS = "SUCCESS";

    private static final String PENDING_STATUS = "PENDING";

    private static final String UNPAID_STATUS = "UNPAID";

    private static final String PAID_STATUS = "PAID";

    private static final String CANCELLED_STATUS = "CANCELLED";

    private static final String CLOSED_STATUS = "CLOSED";

    private static final DateTimeFormatter PAYMENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PaymentMapper paymentMapper;

    private final OrderMapper orderMapper;

    private final InventoryMapper inventoryMapper;

    private final PaymentStrategyRegistry paymentStrategyRegistry;

    private final BehaviorEventService behaviorEventService;

    public PaymentService(
            PaymentMapper paymentMapper,
            OrderMapper orderMapper,
            InventoryMapper inventoryMapper,
            PaymentStrategyRegistry paymentStrategyRegistry,
            BehaviorEventService behaviorEventService
    ) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.inventoryMapper = inventoryMapper;
        this.paymentStrategyRegistry = paymentStrategyRegistry;
        this.behaviorEventService = behaviorEventService;
    }

    /**
     * 对当前用户的未支付订单执行模拟支付。
     *
     * @param userId 当前认证用户 ID，只能来自服务端 JWT 上下文
     * @param request 只包含前端可见订单号，金额和支付状态必须由后端生成
     * @return 支付流水响应；重复支付已支付订单时返回已有成功流水
     */
    @Transactional
    public PaymentResponse mockPay(Long userId, MockPaymentRequest request) {
        return pay(userId, new CreatePaymentRequest(normalizeOrderNo(request), MOCK_CHANNEL));
    }

    /**
     * 通过选定渠道为当前用户的订单创建支付尝试。
     *
     * <p>该请求仅指定订单和支付渠道。订单归属、金额、支付编号、
     * 库存确认以及最终状态更新等逻辑均在此后端事务中处理。</p>
     *
     * @param userId 从 JWT 上下文中获取的当前已认证用户 ID
     * @param request 包含前端选定的订单号和支付渠道的请求对象
     * @return 前端可见的支付状态
     */
    @Transactional
    public PaymentResponse pay(Long userId, CreatePaymentRequest request) {
        validateUserId(userId);
        String orderNo = normalizeOrderNo(request == null ? null : request.orderNo());
        String channel = normalizeChannel(request == null ? null : request.channel());
        PaymentStrategy strategy = paymentStrategyRegistry.getRequired(channel);
        SalesOrder order = orderMapper.findOrderByUserIdAndOrderNoForUpdate(userId, orderNo);
        if (order == null) {
            throw new ResourceNotFoundException("order not found: " + orderNo);
        }
        if (PAID_STATUS.equals(order.getStatus())) {
            return toResponse(findExistingSuccessPayment(order));
        }
        validatePayable(order);

        Payment payment = createPendingPayment(order, channel);
        paymentMapper.insertPayment(payment);

        PaymentResult result = strategy.pay(new PaymentRequestContext(
                payment.getPaymentNo(),
                order.getOrderNo(),
                order.getUserId(),
                order.getTotalAmount(),
                channel
        ));
        if (SUCCESS_STATUS.equals(result.status())) {
            return confirmPaymentSuccess(order, payment, result);
        }
        return toResponse(payment);
    }

    public PaymentResponse getPayment(Long userId, String paymentNo) {
        validateUserId(userId);
        String normalizedPaymentNo = normalizePaymentNo(paymentNo);
        Payment payment = paymentMapper.findByPaymentNo(normalizedPaymentNo);
        if (payment == null || !userId.equals(payment.getUserId())) {
            throw new ResourceNotFoundException("payment not found: " + normalizedPaymentNo);
        }
        return toResponse(payment);
    }

    /**
     * 记录原始的支付服务商回调，但不将其视为支付状态的变更。
     *
     * <p>由于支付服务商无法发送用户 JWT，该回调端点必须公开。因此，本方法在第一阶段仅存储审计数据；
     * 经核实的、特定于渠道的状态变更，将通过“真实服务商阶段”的策略验证机制引入。</p>
     *
     * @param channel 回调路径对应的渠道
     * @param rawBody 原始请求体；即使为空，也会存储以供审计
     * @param request Servlet 请求对象，仅用于获取请求头信息
     */
    public void recordCallback(String channel, String rawBody, HttpServletRequest request) {
        PaymentCallbackLog log = new PaymentCallbackLog();
        log.setChannel(normalizeChannel(channel));
        log.setEventType("RAW_CALLBACK");
        log.setRawBody(rawBody == null ? "" : rawBody);
        log.setHeaders(toHeaderSnapshot(request));
        log.setSignatureValid(false);
        log.setHandled(false);
        paymentMapper.insertCallbackLog(log);
    }

    private Payment findExistingSuccessPayment(SalesOrder order) {
        Payment payment = paymentMapper.findSuccessByOrderId(order.getId());
        if (payment == null) {
            throw new BadRequestException("successful payment record not found for order: " + order.getOrderNo());
        }
        return payment;
    }

    private List<OrderItem> confirmSoldStock(SalesOrder order) {
        List<OrderItem> items = orderMapper.findItemsByOrderId(order.getId());
        for (OrderItem item : items) {
            int affectedRows = inventoryMapper.confirmSoldStock(item.getSkuId(), item.getQuantity());
            if (affectedRows == 0) {
                throw new BadRequestException("locked stock is inconsistent for sku: " + item.getSkuId());
            }
        }
        return items;
    }

    private Payment createPendingPayment(SalesOrder order, String channel) {
        LocalDateTime createdAt = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo(createdAt));
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(order.getUserId());
        payment.setAmount(order.getTotalAmount());
        payment.setChannel(channel);
        payment.setStatus(PENDING_STATUS);
        return payment;
    }

    private PaymentResponse confirmPaymentSuccess(SalesOrder order, Payment payment, PaymentResult result) {
        List<OrderItem> paidItems = confirmSoldStock(order);
        LocalDateTime paidAt = result.paidAt() == null ? LocalDateTime.now() : result.paidAt();
        int updatedRows = paymentMapper.markPaymentSuccess(
                payment.getPaymentNo(),
                result.providerTradeNo(),
                result.transactionId(),
                paidAt
        );
        if (updatedRows == 0) {
            throw new BadRequestException("payment status changed before success confirmation: " + payment.getPaymentNo());
        }
        markOrderPaid(order, paidAt);

        payment.setStatus(SUCCESS_STATUS);
        payment.setProviderTradeNo(result.providerTradeNo());
        payment.setProviderPayload(result.providerPayload());
        payment.setTransactionId(result.transactionId());
        payment.setPaidAt(paidAt);
        recordPaymentSuccessEvents(order, payment, paidItems);
        return toResponse(payment);
    }

    private void recordPaymentSuccessEvents(SalesOrder order, Payment payment, List<OrderItem> paidItems) {
        for (OrderItem item : paidItems) {
            behaviorEventService.recordBusinessEvent(new BehaviorEventCommand(
                    "payment:success:" + payment.getPaymentNo() + ":" + item.getSkuId(),
                    order.getUserId(),
                    "PAYMENT_SUCCESS",
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

    private void markOrderPaid(SalesOrder order, LocalDateTime paidAt) {
        int affectedRows = orderMapper.updateOrderPaid(order.getId(), paidAt);
        if (affectedRows == 0) {
            throw new BadRequestException("order status changed before payment: " + order.getOrderNo());
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentNo(),
                payment.getOrderNo(),
                payment.getAmount(),
                payment.getChannel(),
                payment.getStatus(),
                payment.getTransactionId(),
                payment.getPaidAt()
        );
    }

    private String generatePaymentNo(LocalDateTime paidAt) {
        int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "PAY" + paidAt.format(PAYMENT_TIME_FORMATTER) + suffix;
    }

    private String normalizeOrderNo(MockPaymentRequest request) {
        return normalizeOrderNo(request == null ? null : request.orderNo());
    }

    private String normalizeOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BadRequestException("orderNo must not be blank");
        }
        return orderNo.trim();
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new BadRequestException("channel must not be blank");
        }
        return channel.trim().toUpperCase();
    }

    private String normalizePaymentNo(String paymentNo) {
        if (paymentNo == null || paymentNo.isBlank()) {
            throw new BadRequestException("paymentNo must not be blank");
        }
        return paymentNo.trim();
    }

    private String toHeaderSnapshot(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        return Collections.list(request.getHeaderNames()).stream()
                .map(name -> name + "=" + request.getHeader(name))
                .collect(Collectors.joining("\n"));
    }

    private void validatePayable(SalesOrder order) {
        if (CANCELLED_STATUS.equals(order.getStatus()) || CLOSED_STATUS.equals(order.getStatus())) {
            throw new BadRequestException("closed order cannot be paid: " + order.getOrderNo());
        }
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
