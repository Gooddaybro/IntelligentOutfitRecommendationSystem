package com.recommendation.intelligentoutfitrecommendationsystem.payment;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.mapper.PaymentMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.model.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class PaymentMapperTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(11000);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Test
    void insertsPaymentAndFindsSuccessfulPaymentByOrderId() {
        Long userId = createUser();
        SalesOrder order = insertOrder(userId);
        Payment payment = payment(order, userId);

        paymentMapper.insertPayment(payment);
        Payment found = paymentMapper.findSuccessByOrderId(order.getId());

        assertThat(payment.getId()).isNotNull();
        assertThat(found.getPaymentNo()).isEqualTo(payment.getPaymentNo());
        assertThat(found.getOrderNo()).isEqualTo(order.getOrderNo());
        assertThat(found.getAmount()).isEqualByComparingTo("299.00");
        assertThat(found.getStatus()).isEqualTo("SUCCESS");
    }

    private SalesOrder insertOrder(Long userId) {
        SalesOrder order = new SalesOrder();
        order.setOrderNo("ORDPAYMAPPER" + userId);
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("299.00"));
        order.setStatus("UNPAID");
        orderMapper.insertOrder(order);
        return order;
    }

    private Payment payment(SalesOrder order, Long userId) {
        Payment payment = new Payment();
        payment.setPaymentNo("PAYMAPPER" + userId);
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(userId);
        payment.setAmount(new BigDecimal("299.00"));
        payment.setChannel("MOCK");
        payment.setStatus("SUCCESS");
        payment.setTransactionId("mock-transaction-" + userId);
        payment.setPaidAt(LocalDateTime.now());
        return payment;
    }

    private Long createUser() {
        String username = "payment_mapper_user_" + USER_SEQUENCE.incrementAndGet();
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash("encoded-password");
        userAccount.setStatus("active");
        userAuthMapper.insertUserAccount(userAccount);

        Long roleId = userAuthMapper.findRoleIdByCode("USER");
        userAuthMapper.insertUserRole(userAccount.getId(), roleId);
        return userAccount.getId();
    }
}
