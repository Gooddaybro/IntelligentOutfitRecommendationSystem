package com.recommendation.intelligentoutfitrecommendationsystem.payment.service;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService.OrderItemView;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService.OrderView;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.CreatePaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.MockPaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.PaymentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.ProviderPaymentCallback;
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
 * 支付业务服务。
 *
 * 该服务是所有支付渠道的事务边界：策略只能创建渠道结果，订单状态、支付状态、
 * 库存确认和行为事件都必须在这里统一处理，防止真实回调和用户请求形成两套交易路径。
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

    private static final String CALLBACK_REJECTED_EVENT = "CALLBACK_REJECTED";

    private static final String PAYMENT_SUCCESS_EVENT = "PAYMENT_SUCCESS";

    private static final String DUPLICATE_PAYMENT_SUCCESS_EVENT = "DUPLICATE_PAYMENT_SUCCESS";

    private static final DateTimeFormatter PAYMENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PaymentMapper paymentMapper;

    private final OrderApplicationService orderApplicationService;

    private final InventoryApplicationService inventoryApplicationService;

    private final PaymentStrategyRegistry paymentStrategyRegistry;

    private final BehaviorEventService behaviorEventService;

    private final PaymentCallbackVerifier paymentCallbackVerifier;
    private final ApplicationMetrics metrics;

    public PaymentService(
            PaymentMapper paymentMapper,
            OrderApplicationService orderApplicationService,
            InventoryApplicationService inventoryApplicationService,
            PaymentStrategyRegistry paymentStrategyRegistry,
            BehaviorEventService behaviorEventService,
            PaymentCallbackVerifier paymentCallbackVerifier,
            ApplicationMetrics metrics
    ) {
        this.paymentMapper = paymentMapper;
        this.orderApplicationService = orderApplicationService;
        this.inventoryApplicationService = inventoryApplicationService;
        this.paymentStrategyRegistry = paymentStrategyRegistry;
        this.behaviorEventService = behaviorEventService;
        this.paymentCallbackVerifier = paymentCallbackVerifier;
        this.metrics = metrics;
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
        OrderView order = orderApplicationService.lockOwnedOrder(userId, orderNo);
        if (order == null) {
            throw new ResourceNotFoundException("order not found: " + orderNo);
        }
        if (PAID_STATUS.equals(order.status())) {
            return toResponse(findExistingSuccessPayment(order));
        }
        validatePayable(order);
        Payment pendingPayment = paymentMapper.findPendingByOrderId(order.id());
        if (pendingPayment != null) {
            return toResponse(pendingPayment);
        }

        Payment payment = createPendingPayment(order, channel);
        PaymentResult result = strategy.pay(new PaymentRequestContext(
                payment.getPaymentNo(),
                order.orderNo(),
                order.userId(),
                order.totalAmount(),
                channel
        ));
        applyProviderResult(payment, result);
        paymentMapper.insertPayment(payment);
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

    /**
     * 处理已公开暴露的支付服务商回调。
     *
     * 公开回调不能依赖用户 JWT，因此所有状态变更必须先通过渠道签名校验，再锁定 Java
     * 创建的支付流水和订单行，确认渠道、金额、订单号完全匹配后才能进入成功确认路径。
     */
    @Transactional
    public void handleCallback(String channel, String rawBody, HttpServletRequest request) {
        String normalizedChannel = normalizeChannel(channel);
        PaymentCallbackVerification verification = paymentCallbackVerifier.verify(normalizedChannel, rawBody, request);
        if (!verification.valid()) {
            metrics.recordPaymentCallback("invalid_signature");
            paymentMapper.insertCallbackLog(rejectedCallbackLog(
                    normalizedChannel,
                    rawBody,
                    request,
                    verification.failureReason()
            ));
            return;
        }

        ProviderPaymentCallback callback = verification.callback();
        PaymentCallbackLog log = verifiedCallbackLog(normalizedChannel, rawBody, request, callback);
        if (!SUCCESS_STATUS.equals(normalizeStatus(callback.status()))) {
            metrics.recordPaymentCallback("rejected");
            log.setEventType("CALLBACK_IGNORED");
            log.setFailureReason("callback status is not success");
            paymentMapper.insertCallbackLog(log);
            return;
        }

        Payment payment = paymentMapper.findByPaymentNoForUpdate(callback.paymentNo());
        String failureReason = validateCallbackPayment(normalizedChannel, callback, payment);
        if (failureReason != null) {
            metrics.recordPaymentCallback("rejected");
            log.setFailureReason(failureReason);
            paymentMapper.insertCallbackLog(log);
            return;
        }
        if (SUCCESS_STATUS.equals(payment.getStatus())) {
            metrics.recordPaymentCallback("duplicate");
            log.setEventType(DUPLICATE_PAYMENT_SUCCESS_EVENT);
            log.setHandled(true);
            paymentMapper.insertCallbackLog(log);
            return;
        }

        OrderView order = orderApplicationService.lockOrder(callback.orderNo());
        failureReason = validateCallbackOrder(callback, payment, order);
        if (failureReason != null) {
            metrics.recordPaymentCallback("rejected");
            log.setFailureReason(failureReason);
            paymentMapper.insertCallbackLog(log);
            return;
        }

        confirmPaymentSuccess(order, payment, new PaymentResult(
                payment.getPaymentNo(),
                payment.getChannel(),
                SUCCESS_STATUS,
                callback.providerTradeNo(),
                callback.transactionId(),
                callback.providerPayload(),
                callback.paidAt()
        ));
        log.setHandled(true);
        paymentMapper.insertCallbackLog(log);
        metrics.recordPaymentCallback("success");
    }

    private Payment findExistingSuccessPayment(OrderView order) {
        Payment payment = paymentMapper.findSuccessByOrderId(order.id());
        if (payment == null) {
            throw new BadRequestException("successful payment record not found for order: " + order.orderNo());
        }
        return payment;
    }

    private List<OrderItemView> confirmSoldStock(OrderView order) {
        List<OrderItemView> items = orderApplicationService.findItems(order.id());
        for (OrderItemView item : items) {
            inventoryApplicationService.confirm(item.skuId(), item.quantity());
        }
        return items;
    }

    private Payment createPendingPayment(OrderView order, String channel) {
        LocalDateTime createdAt = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo(createdAt));
        payment.setOrderId(order.id());
        payment.setOrderNo(order.orderNo());
        payment.setUserId(order.userId());
        payment.setAmount(order.totalAmount());
        payment.setChannel(channel);
        payment.setStatus(PENDING_STATUS);
        return payment;
    }

    private void applyProviderResult(Payment payment, PaymentResult result) {
        if (result == null) {
            return;
        }
        payment.setProviderTradeNo(result.providerTradeNo());
        payment.setProviderPayload(result.providerPayload());
        payment.setTransactionId(result.transactionId());
        if (!SUCCESS_STATUS.equals(result.status()) && result.status() != null) {
            payment.setStatus(result.status());
        }
        payment.setPaidAt(result.paidAt());
    }

    private PaymentResponse confirmPaymentSuccess(OrderView order, Payment payment, PaymentResult result) {
        List<OrderItemView> paidItems = confirmSoldStock(order);
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
        orderApplicationService.markPaid(order, paidAt);

        payment.setStatus(SUCCESS_STATUS);
        payment.setProviderTradeNo(result.providerTradeNo());
        payment.setProviderPayload(result.providerPayload());
        payment.setTransactionId(result.transactionId());
        payment.setPaidAt(paidAt);
        recordPaymentSuccessEvents(order, payment, paidItems);
        return toResponse(payment);
    }

    private void recordPaymentSuccessEvents(OrderView order, Payment payment, List<OrderItemView> paidItems) {
        for (OrderItemView item : paidItems) {
            behaviorEventService.recordBusinessEvent(new BehaviorEventCommand(
                    "payment:success:" + payment.getPaymentNo() + ":" + item.skuId(),
                    order.userId(),
                    "PAYMENT_SUCCESS",
                    null,
                    item.spuId(),
                    item.skuId(),
                    null,
                    null,
                    order.orderNo(),
                    item.quantity(),
                    null
            ));
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

    private PaymentCallbackLog rejectedCallbackLog(
            String channel,
            String rawBody,
            HttpServletRequest request,
            String failureReason
    ) {
        PaymentCallbackLog log = new PaymentCallbackLog();
        log.setChannel(channel);
        log.setEventType(CALLBACK_REJECTED_EVENT);
        log.setRawBody(rawBody == null ? "" : rawBody);
        log.setHeaders(toHeaderSnapshot(request));
        log.setSignatureValid(false);
        log.setHandled(false);
        log.setFailureReason(failureReason);
        return log;
    }

    private PaymentCallbackLog verifiedCallbackLog(
            String channel,
            String rawBody,
            HttpServletRequest request,
            ProviderPaymentCallback callback
    ) {
        PaymentCallbackLog log = new PaymentCallbackLog();
        log.setChannel(channel);
        log.setPaymentNo(callback.paymentNo());
        log.setOrderNo(callback.orderNo());
        log.setProviderTradeNo(callback.providerTradeNo());
        log.setEventType(PAYMENT_SUCCESS_EVENT);
        log.setRawBody(rawBody == null ? "" : rawBody);
        log.setHeaders(toHeaderSnapshot(request));
        log.setSignatureValid(true);
        log.setHandled(false);
        return log;
    }

    private String validateCallbackPayment(String channel, ProviderPaymentCallback callback, Payment payment) {
        if (payment == null) {
            return "payment not found";
        }
        if (!channel.equals(payment.getChannel())) {
            return "payment channel mismatch";
        }
        if (!callback.orderNo().equals(payment.getOrderNo())) {
            return "payment order mismatch";
        }
        if (callback.amount().compareTo(payment.getAmount()) != 0) {
            return "payment amount mismatch";
        }
        if (!PENDING_STATUS.equals(payment.getStatus()) && !SUCCESS_STATUS.equals(payment.getStatus())) {
            return "payment status is not callback-confirmable";
        }
        return null;
    }

    private String validateCallbackOrder(ProviderPaymentCallback callback, Payment payment, OrderView order) {
        if (order == null) {
            return "order not found";
        }
        if (!payment.getOrderId().equals(order.id()) || !payment.getUserId().equals(order.userId())) {
            return "order ownership mismatch";
        }
        if (!callback.orderNo().equals(order.orderNo())) {
            return "order number mismatch";
        }
        if (!UNPAID_STATUS.equals(order.status())) {
            return "order is not unpaid";
        }
        return null;
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

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
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

    private void validatePayable(OrderView order) {
        if (CANCELLED_STATUS.equals(order.status()) || CLOSED_STATUS.equals(order.status())) {
            throw new BadRequestException("closed order cannot be paid: " + order.orderNo());
        }
        if (!UNPAID_STATUS.equals(order.status())) {
            throw new BadRequestException("order is not unpaid: " + order.orderNo());
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }
}
