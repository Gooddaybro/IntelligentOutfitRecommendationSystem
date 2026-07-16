package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorSummaryService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;

/**
 * 组装 Java 业务上下文给 Python AI 服务。
 *
 * Python 不直接读取 Java 数据库；它拿到的是用户画像、会话历史和 Java 过滤后的候选商品池。
 */
@Service
public class AssistantContextService {

    private final UserProfileService userProfileService;
    private final RecommendationCandidateQueryService recommendationCandidateQueryService;
    private final ConversationApplicationService conversationService;
    private final BehaviorSummaryService behaviorSummaryService;
    private final DemandIntentResolver demandIntentResolver = new DemandIntentResolver();

    @Autowired
    public AssistantContextService(
            UserProfileService userProfileService,
            RecommendationCandidateQueryService recommendationCandidateQueryService,
            ConversationApplicationService conversationService,
            BehaviorSummaryService behaviorSummaryService
    ) {
        this.userProfileService = userProfileService;
        this.recommendationCandidateQueryService = recommendationCandidateQueryService;
        this.conversationService = conversationService;
        this.behaviorSummaryService = behaviorSummaryService;
    }

    public AssistantContextService(
            UserProfileService userProfileService,
            RecommendationCandidateQueryService recommendationCandidateQueryService,
            ConversationApplicationService conversationService
    ) {
        this(userProfileService, recommendationCandidateQueryService, conversationService, null);
    }

    public AssistantContext buildContext(Long userId, String threadId, AssistantChatRequest request) {
        UserProfileResponse profile = userProfileService.getProfile(userId);
        UserBodyDataResponse bodyData = userProfileService.getBodyData(userId);
        DemandIntent initialIntent = demandIntentResolver.resolve(request, bodyData, profile);
        String requestId = MDC.get("requestId");
        if (!hasText(requestId)) {
            requestId = "local-" + UUID.randomUUID();
        }
        DemandIntent persistedIntent = conversationService.applyDemandPatch(
                userId,
                threadId,
                requestId,
                null,
                demandIntentResolver.resolvePatch(request),
                initialIntent
        );
        DemandIntent demandIntent = persistedIntent == null ? initialIntent : persistedIntent;
        // Java 只执行 DemandIntent 中的硬过滤；候选池内的排序解释仍由 Python AI 服务完成。
        RecommendationCandidateQuery query = new RecommendationCandidateQuery(
                demandIntent.category(),
                first(demandIntent.style()),
                seasonFilter(request, demandIntent),
                request.material(),
                request.fit(),
                demandIntent.budgetMax(),
                demandIntent.targetGender()
        );
        return new AssistantContext(
                profile,
                bodyData,
                userProfileService.getPreferences(userId),
                behaviorSummaryService == null ? null : behaviorSummaryService.getSummary(userId),
                conversationService.getMessages(userId, threadId),
                recommendationCandidateQueryService.findCandidates(query),
                demandIntent
        );
    }

    private String seasonFilter(AssistantChatRequest request, DemandIntent demandIntent) {
        if (hasText(request.season())) {
            return request.season().trim();
        }
        String rawQuery = demandIntent.rawQuery();
        // 自然语言季节词必须落到数据库已有 code，避免 Python 收到一池明显不相关的候选。
        if (demandIntent.attributes().contains("保暖")
                || containsAny(rawQuery, "秋冬", "冬季", "冬天", "冬", "保暖", "厚款", "厚实", "怕冷")) {
            return "winter";
        }
        if (containsAny(rawQuery, "秋季", "秋天", "秋")) {
            return "autumn";
        }
        if (containsAny(rawQuery, "夏季", "夏天", "夏")) {
            return "summer";
        }
        if (containsAny(rawQuery, "春季", "春天", "春")) {
            return "spring";
        }
        return null;
    }

    private boolean containsAny(String text, String... signals) {
        if (!hasText(text)) {
            return false;
        }
        for (String signal : signals) {
            if (text.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String first(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
