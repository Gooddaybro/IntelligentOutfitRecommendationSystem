package com.recommendation.intelligentoutfitrecommendationsystem.aftersale.api;

import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto.AfterSaleResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto.CancelAfterSaleRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto.CreateAfterSaleRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.service.AfterSaleService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录用户售后接口。
 *
 * 售后申请只能由订单归属用户发起和撤销；本接口不暴露运营审核或真实退款动作，避免用户
 * 请求直接驱动资金状态。
 */
@RestController
@RequestMapping("/api/after-sales")
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    public AfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    @PostMapping
    public ApiResponse<AfterSaleResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateAfterSaleRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(afterSaleService.create(currentUser.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<AfterSaleResponse>> list(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(afterSaleService.list(currentUser.userId()));
    }

    @PostMapping("/{requestNo}/cancel")
    public ApiResponse<AfterSaleResponse> cancel(
            Authentication authentication,
            @PathVariable String requestNo,
            @RequestBody(required = false) CancelAfterSaleRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(afterSaleService.cancel(currentUser.userId(), requestNo, request));
    }
}
