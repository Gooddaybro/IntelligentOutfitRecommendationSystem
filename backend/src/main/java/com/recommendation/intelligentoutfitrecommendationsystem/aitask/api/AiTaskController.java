package com.recommendation.intelligentoutfitrecommendationsystem.aitask.api;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto.AiTaskResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto.CreateAiTaskRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto.RedriveAiTaskRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员专属 AI 长任务 API；普通用户不能触发全局知识索引重建。
 */
@RestController
@RequestMapping("/api/ai/tasks")
public class AiTaskController {

    private final AiTaskApplicationService applicationService;

    public AiTaskController(AiTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AiTaskResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateAiTaskRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String correlationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent
    ) {
        CurrentUser user = CurrentUser.from(authentication);
        AiTaskResponse response = applicationService.createTask(
                user.userId(), request, correlationId, traceparent
        );
        return ResponseEntity.accepted().body(ApiResponse.ok(response));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<AiTaskResponse> get(@PathVariable String taskId) {
        return ApiResponse.ok(applicationService.getTask(taskId));
    }

    @PostMapping("/{taskId}/redrive")
    public ResponseEntity<ApiResponse<AiTaskResponse>> redrive(
            Authentication authentication,
            @PathVariable String taskId,
            @Valid @RequestBody(required = false) RedriveAiTaskRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String correlationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent
    ) {
        CurrentUser user = CurrentUser.from(authentication);
        AiTaskResponse response = applicationService.redrive(
                taskId, user.userId(), request, correlationId, traceparent
        );
        return ResponseEntity.accepted().body(ApiResponse.ok(response));
    }
}
