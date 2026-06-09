package com.recommendation.intelligentoutfitrecommendationsystem.payment.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.payment.model.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 支付流水数据访问入口。
 *
 * 支付流水以 orderId 作为内部关联边界，用于支持重复支付幂等返回和未来真实支付回调审计。
 */
@Mapper
public interface PaymentMapper {

    void insertPayment(Payment payment);

    Payment findSuccessByOrderId(@Param("orderId") Long orderId);
}
