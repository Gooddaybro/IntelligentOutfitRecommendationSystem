package com.recommendation.intelligentoutfitrecommendationsystem.assistant.api;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProDoneEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProAssistantService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 独立的 Pro 同步入口，只接受用户意图；身份取自认证主体。
 * 同步和流式入口都由 Pro 服务负责最终事实校验、持久化和终结。
 */
@RestController
@RequestMapping("/api/assistant/v2")
public class ProAssistantController {
    private final ProAssistantService service;

    public ProAssistantController(ProAssistantService service) {
        this.service = service;
    }

    /** 同步结果与 SSE done 使用同一份 Java 校验后的商品事实。 */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ProDoneEvent>> chat(Authentication authentication,
                                                 @Valid @RequestBody ProChatRequest request) {
        Long userId = CurrentUser.from(authentication).userId();
        ProDoneEvent result = service.chat(userId, request);
        if (result != null) {
            return ResponseEntity.ok(ApiResponse.ok(result));
        }
        // Compatibility path for callers compiled against the Task 3 validation-only mock boundary.
        service.exchangeForValidation(userId, request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("pro_result_not_ready", "Pro 结果校验尚未启用"));
    }

    /**
     * Pro v2 public SSE endpoint; progress is forwarded early and answer tokens wait for Java validation.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(Authentication authentication,
                                 @Valid @RequestBody ProChatRequest request) {
        return service.streamChat(CurrentUser.from(authentication).userId(), request);
    }

    /** 功能开关关闭时使用明确错误码，不回退到 Lite。 */
    @ExceptionHandler(ProAssistantService.ProUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> unavailable(ProAssistantService.ProUnavailableException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(error.getMessage(), "Pro 尚未启用"));
    }
}
