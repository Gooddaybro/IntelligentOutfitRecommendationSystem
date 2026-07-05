package com.recommendation.intelligentoutfitrecommendationsystem.payment.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.payment.model.Payment;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.model.PaymentCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 支付流水数据访问入口。
 *
 * 支付流水以 orderId 作为内部关联边界，用于支持重复支付幂等返回和未来真实支付回调审计。
 */
@Mapper
public interface PaymentMapper {

    void insertPayment(Payment payment);

    Payment findSuccessByOrderId(@Param("orderId") Long orderId);

    /**
     * 查询订单当前仍在等待服务商回调的支付尝试。
     *
     * 一笔订单同一时间只保留一个 PENDING 流水，重复点击支付时返回这条流水，
     * 避免违反数据库唯一约束，也避免向支付服务商重复创建订单。
     */
    Payment findPendingByOrderId(@Param("orderId") Long orderId);

    Payment findByPaymentNo(@Param("paymentNo") String paymentNo);

    /**
     * 按支付业务号锁定支付流水。
     *
     * 公开回调没有用户 JWT，只能先通过已验签的 paymentNo 锁定 Java 自己创建的流水，
     * 再校验订单、金额和渠道是否完全匹配。
     */
    Payment findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    int markPaymentSuccess(
            @Param("paymentNo") String paymentNo,
            @Param("providerTradeNo") String providerTradeNo,
            @Param("transactionId") String transactionId,
            @Param("paidAt") LocalDateTime paidAt
    );

    void insertCallbackLog(PaymentCallbackLog log);
}
