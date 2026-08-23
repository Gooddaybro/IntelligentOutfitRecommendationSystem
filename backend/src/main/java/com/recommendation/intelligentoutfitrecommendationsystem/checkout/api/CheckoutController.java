package com.recommendation.intelligentoutfitrecommendationsystem.checkout.api;

import com.recommendation.intelligentoutfitrecommendationsystem.checkout.dto.CheckoutPreviewRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.dto.CheckoutPreviewResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.service.CheckoutCalculator;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城前端的可信结算预览入口。
 *
 * Controller 只接收商品选择和地址，用户归属来自 JWT，金额与数量全部交给 CheckoutCalculator。
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutCalculator checkoutCalculator;

    public CheckoutController(CheckoutCalculator checkoutCalculator) {
        this.checkoutCalculator = checkoutCalculator;
    }

    @PostMapping("/preview")
    public ApiResponse<CheckoutPreviewResponse> preview(
            Authentication authentication,
            @Valid @RequestBody CheckoutPreviewRequest request
    ) {
        Long userId = CurrentUser.from(authentication).userId();
        return ApiResponse.ok(CheckoutPreviewResponse.from(
                checkoutCalculator.previewCart(userId, request.skuIds(), request.addressId())
        ));
    }
}
