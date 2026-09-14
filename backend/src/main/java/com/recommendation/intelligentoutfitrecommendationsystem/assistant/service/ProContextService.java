package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProPythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatHistoryItem;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorSummaryService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从已认证用户的 Java 资料和会话装配 Pro v2 上下文；需求理解与商品召回交给 Python 工具流程。
 * 显式筛选与软偏好保持分离，避免画像被误当成本轮硬约束。
 */
@Service
public class ProContextService {
    private final UserProfileService profiles;
    private final ConversationApplicationService conversations;
    private final BehaviorSummaryService behaviors;

    public ProContextService(UserProfileService profiles, ConversationApplicationService conversations,
                             BehaviorSummaryService behaviors) {
        this.profiles = profiles;
        this.conversations = conversations;
        this.behaviors = behaviors;
    }

    /**
     * 使用服务端解析的会话和运行凭证装配请求，历史读取再次检查用户所有权。
     * 当前已保存但未回答的用户消息不进入历史；本服务不会调用 Lite 解析器或预筛商品。
     */
    public ProPythonChatRequest build(Long userId, String threadId, ProChatRequest request,
                                      String requestId, ProRunRegistry.RunCredentials credentials) {
        List<PythonChatHistoryItem> history = completedHistory(conversations.getMessages(userId, threadId), requestId);
        return new ProPythonChatRequest("assistant-v2", "pro", requestId, credentials.runId(), threadId,
                request.message(), history, userContext(userId), explicitFilters(request), List.of(), credentials.token());
    }

    /**
     * 只从用户资料服务读取事实；身体数据的性别优先，缺失时保留基础资料回退。
     * 不传递昵称、头像或生日等本次穿搭推理不需要的账号资料。
     */
    private Map<String, Object> userContext(Long userId) {
        var profile = profiles.getProfile(userId);
        var body = profiles.getBodyData(userId);
        var preferences = profiles.getPreferences(userId);
        var summary = behaviors.getSummary(userId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("user_id", userId);
        putPresent(context, "gender", body != null && body.gender() != null
                ? body.gender() : profile == null ? null : profile.gender());
        if (body != null) {
            putPresent(context, "height_cm", body.heightCm());
            putPresent(context, "weight_kg", body.weightKg());
            putPresent(context, "preferred_fit", body.preferredFit());
        }
        if (preferences != null) {
            putPresent(context, "preferred_styles", preferences.preferredStyles());
            putPresent(context, "preferred_colors", preferences.preferredColors());
            putPresent(context, "disliked_colors", preferences.dislikedColors());
            putPresent(context, "preferred_categories", preferences.preferredCategories());
            putPresent(context, "budget_min", preferences.budgetMin());
            putPresent(context, "budget_max", preferences.budgetMax());
        }
        if (summary != null) {
            context.put("recent_interest_spu_ids", summary.recentInterestSpuIds());
            context.put("recent_cart_spu_ids", summary.recentCartSpuIds());
            context.put("recent_purchased_spu_ids", summary.recentPurchasedSpuIds());
            context.put("behavior_preferred_categories", summary.preferredCategories());
            context.put("behavior_preferred_styles", summary.preferredStyles());
        }
        return Map.copyOf(context);
    }

    private void putPresent(Map<String, Object> context, String key, Object value) {
        if (value != null) {
            context.put(key, value);
        }
    }

    private Map<String, String> explicitFilters(ProChatRequest request) {
        Map<String, String> filters = new LinkedHashMap<>();
        putFilter(filters, "category", request.category());
        putFilter(filters, "style", request.style());
        putFilter(filters, "season", request.season());
        putFilter(filters, "material", request.material());
        putFilter(filters, "fit", request.fit());
        putFilter(filters, "gender", request.gender());
        if (request.budgetMax() != null) {
            filters.put("budget_max", request.budgetMax().toPlainString());
        }
        return Map.copyOf(filters);
    }

    private void putFilter(Map<String, String> filters, String key, String value) {
        if (value != null && !value.isBlank()) {
            filters.put(key, value.trim());
        }
    }

    /**
     * 只配对成功持久化的问答并保留最近一百轮，以满足 v2 历史大小边界。
     * 只接受请求 ID 一致的相邻成功问答，排除本轮及缺少关联 ID 的旧数据，避免晚到回答配错问题。
     */
    private List<PythonChatHistoryItem> completedHistory(List<MessageResponse> messages, String currentRequestId) {
        List<PythonChatHistoryItem> turns = new ArrayList<>();
        MessageResponse pending = null;
        for (MessageResponse message : messages) {
            if ("user".equalsIgnoreCase(message.role())) {
                pending = "succeeded".equals(message.messageStatus())
                        && message.requestId() != null && !message.requestId().isBlank()
                        && !message.requestId().equals(currentRequestId) ? message : null;
            } else if ("assistant".equalsIgnoreCase(message.role())) {
                if (pending != null && "succeeded".equals(message.messageStatus())
                        && pending.requestId().equals(message.requestId())) {
                    turns.add(new PythonChatHistoryItem(bounded(pending.content(), 2000),
                            bounded(message.content(), 8000)));
                }
                pending = null;
            }
        }
        return List.copyOf(turns.subList(Math.max(0, turns.size() - 100), turns.size()));
    }

    private String bounded(String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), limit));
    }
}
