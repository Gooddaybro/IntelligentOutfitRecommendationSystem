package com.recommendation.intelligentoutfitrecommendationsystem.payment.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.CreatePaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.MockPaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.PaymentCallbackResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.PaymentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.service.PaymentService;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current user's public payment API.
 *
 * <p>Authenticated endpoints let the user create and inspect payment attempts. The callback endpoint
 * is public for provider delivery, but the service treats its payload as untrusted until a strategy
 * verifies channel-specific signatures in later phases.</p>
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ApiResponse<PaymentResponse> pay(
            Authentication authentication,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(paymentService.pay(currentUser.userId(), request));
    }

    @PostMapping("/mock-pay")
    public ApiResponse<PaymentResponse> mockPay(
            Authentication authentication,
            @Valid @RequestBody MockPaymentRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(paymentService.mockPay(currentUser.userId(), request));
    }

    @GetMapping("/{paymentNo}")
    public ApiResponse<PaymentResponse> getPayment(Authentication authentication, @PathVariable String paymentNo) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(paymentService.getPayment(currentUser.userId(), paymentNo));
    }

    @PostMapping("/callback/{channel}")
    public ApiResponse<PaymentCallbackResponse> callback(
            @PathVariable String channel,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest request
    ) {
        paymentService.handleCallback(channel, rawBody, request);
        return ApiResponse.ok(new PaymentCallbackResponse(true, channel));
    }
}
