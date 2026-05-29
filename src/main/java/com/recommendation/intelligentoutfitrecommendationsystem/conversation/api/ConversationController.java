package com.recommendation.intelligentoutfitrecommendationsystem.conversation.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.CreateConversationRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录用户的 AI 会话接口。
 *
 * 会话历史是 assistant-service 组装 Python 上下文的来源，只允许通过 JWT 中的 userId 访问本人数据。
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ApiResponse<ConversationResponse> createConversation(
            Authentication authentication,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(conversationService.createConversation(currentUser.userId(), request.title()));
    }

    @GetMapping
    public ApiResponse<List<ConversationResponse>> listConversations(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(conversationService.listConversations(currentUser.userId()));
    }

    @GetMapping("/{threadId}/messages")
    public ApiResponse<List<MessageResponse>> getMessages(
            Authentication authentication,
            @PathVariable String threadId
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(conversationService.getMessages(currentUser.userId(), threadId));
    }

    @DeleteMapping("/{threadId}")
    public ApiResponse<Void> archiveConversation(
            Authentication authentication,
            @PathVariable String threadId
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        conversationService.archiveConversation(currentUser.userId(), threadId);
        return ApiResponse.ok(null);
    }
}
