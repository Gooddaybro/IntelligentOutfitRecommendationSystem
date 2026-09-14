package com.recommendation.intelligentoutfitrecommendationsystem.assistant.api;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProAssistantService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 独立的 Pro 同步入口，只接受用户意图；身份取自认证主体。
 * Task 3 仅打通内部传输，在最终结果校验实现前明确返回不可用，避免泄露未校验回答。
 */
@RestController
@RequestMapping("/api/assistant/v2")
public class ProAssistantController {
    private final ProAssistantService service;

    public ProAssistantController(ProAssistantService service) {
        this.service = service;
    }

    /** 后续结果校验阶段接入此处；目前内部响应既不透传前端，也不保存为助手消息。 */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Void>> chat(Authentication authentication,
                                                 @Valid @RequestBody ProChatRequest request) {
        service.exchangeForValidation(CurrentUser.from(authentication).userId(), request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("pro_result_not_ready", "Pro 结果校验尚未启用"));
    }

    /** 功能开关关闭时使用明确错误码，不回退到 Lite。 */
    @ExceptionHandler(ProAssistantService.ProUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> unavailable(ProAssistantService.ProUnavailableException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(error.getMessage(), "Pro 尚未启用"));
    }
}
