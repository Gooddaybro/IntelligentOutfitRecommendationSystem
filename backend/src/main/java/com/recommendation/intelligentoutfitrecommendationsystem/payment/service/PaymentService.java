package com.recommendation.intelligentoutfitrecommendationsystem.payment.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.MockPaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.PaymentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.mapper.PaymentMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.model.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

    private static final String UNPAID_STATUS = "UNPAID";

    private static final String PAID_STATUS = "PAID";

    private static final String CANCELLED_STATUS = "CANCELLED";

    private static final String CLOSED_STATUS = "CLOSED";

    private static final DateTimeFormatter PAYMENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PaymentMapper paymentMapper;

    private final OrderMapper orderMapper;

    private final InventoryMapper inventoryMapper;

    public PaymentService(PaymentMapper paymentMapper, OrderMapper orderMapper, InventoryMapper inventoryMapper) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.inventoryMapper = inventoryMapper;
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
        validateUserId(userId);
        String orderNo = normalizeOrderNo(request);
        SalesOrder order = orderMapper.findOrderByUserIdAndOrderNoForUpdate(userId, orderNo);
        if (order == null) {
            throw new ResourceNotFoundException("order not found: " + orderNo);
        }
        if (PAID_STATUS.equals(order.getStatus())) {
            return toResponse(findExistingSuccessPayment(order));
        }
        if (CANCELLED_STATUS.equals(order.getStatus()) || CLOSED_STATUS.equals(order.getStatus())) {
            throw new BadRequestException("closed order cannot be paid: " + orderNo);
        }
        if (!UNPAID_STATUS.equals(order.getStatus())) {
            throw new BadRequestException("order is not unpaid: " + orderNo);
        }

        confirmSoldStock(order);
        LocalDateTime paidAt = LocalDateTime.now();
        Payment payment = createSuccessPayment(order, paidAt);
        paymentMapper.insertPayment(payment);
        markOrderPaid(order, paidAt);
        return toResponse(payment);
    }

    private Payment findExistingSuccessPayment(SalesOrder order) {
        Payment payment = paymentMapper.findSuccessByOrderId(order.getId());
        if (payment == null) {
            throw new BadRequestException("successful payment record not found for order: " + order.getOrderNo());
        }
        return payment;
    }

    private void confirmSoldStock(SalesOrder order) {
        List<OrderItem> items = orderMapper.findItemsByOrderId(order.getId());
        for (OrderItem item : items) {
            int affectedRows = inventoryMapper.confirmSoldStock(item.getSkuId(), item.getQuantity());
            if (affectedRows == 0) {
                throw new BadRequestException("locked stock is inconsistent for sku: " + item.getSkuId());
            }
        }
    }

    private Payment createSuccessPayment(SalesOrder order, LocalDateTime paidAt) {
        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo(paidAt));
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(order.getUserId());
        payment.setAmount(order.getTotalAmount());
        payment.setChannel(MOCK_CHANNEL);
        payment.setStatus(SUCCESS_STATUS);
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setPaidAt(paidAt);
        return payment;
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
        if (request == null || request.orderNo() == null || request.orderNo().isBlank()) {
            throw new BadRequestException("orderNo must not be blank");
        }
        return request.orderNo().trim();
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }
}
