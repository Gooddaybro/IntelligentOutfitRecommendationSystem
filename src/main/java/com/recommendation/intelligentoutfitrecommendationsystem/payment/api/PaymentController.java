package com.recommendation.intelligentoutfitrecommendationsystem.payment.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.MockPaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.PaymentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.service.PaymentService;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户的模拟支付接口。
 *
 * 该 Controller 只用于 Java 后端交易闭环验证，不对 Python AI 服务开放；AI 推荐不能直接触发支付，
 * 用户必须在前端携带 JWT 主动确认支付。
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/mock-pay")
    public ApiResponse<PaymentResponse> mockPay(
            Authentication authentication,
            @Valid @RequestBody MockPaymentRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(paymentService.mockPay(currentUser.userId(), request));
    }
}
