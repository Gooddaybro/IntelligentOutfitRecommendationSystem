package com.recommendation.intelligentoutfitrecommendationsystem.assistant.api;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantService;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationAttributionService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 面向前端的 AI 导购接口。
 *
 * Controller 只处理用户鉴权和入参校验，AI 上下文组装与 Python 调用统一放在 assistant-service。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final RecommendationAttributionService recommendationAttributionService;

    public AssistantController(
            AssistantService assistantService,
            RecommendationAttributionService recommendationAttributionService
    ) {
        this.assistantService = assistantService;
        this.recommendationAttributionService = recommendationAttributionService;
    }

    @PostMapping("/chat")
    public ApiResponse<AssistantChatResponse> chat(
            Authentication authentication,
            @Valid @RequestBody AssistantChatRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(assistantService.chat(currentUser.userId(), request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            Authentication authentication,
            @Valid @RequestBody AssistantChatRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return assistantService.streamChat(currentUser.userId(), request);
    }

    @GetMapping("/recommendations/{recommendationId}/candidates")
    public ApiResponse<List<RecommendationCandidate>> recommendationCandidates(
            Authentication authentication,
            @PathVariable String recommendationId
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(recommendationAttributionService.getCandidateSnapshot(
                currentUser.userId(), recommendationId));
    }
}
