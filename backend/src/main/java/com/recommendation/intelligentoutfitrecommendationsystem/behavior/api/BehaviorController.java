package com.recommendation.intelligentoutfitrecommendationsystem.behavior.api;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorEventRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorEventResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorSummaryService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户的推荐行为反馈接口。
 */
@RestController
public class BehaviorController {

    private final BehaviorEventService behaviorEventService;

    private final BehaviorSummaryService behaviorSummaryService;

    public BehaviorController(BehaviorEventService behaviorEventService, BehaviorSummaryService behaviorSummaryService) {
        this.behaviorEventService = behaviorEventService;
        this.behaviorSummaryService = behaviorSummaryService;
    }

    @PostMapping("/api/behavior/events")
    public ApiResponse<BehaviorEventResponse> recordEvent(
            Authentication authentication,
            @Valid @RequestBody BehaviorEventRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(behaviorEventService.recordRecommendationInteraction(currentUser.userId(), request));
    }

    @GetMapping("/api/me/behavior-summary")
    public ApiResponse<BehaviorSummaryResponse> getSummary(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(behaviorSummaryService.getSummary(currentUser.userId()));
    }
}
