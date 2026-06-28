package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 组装 Java 业务上下文给 Python AI 服务。
 *
 * Python 不直接读 Java 数据库；它拿到的是用户画像、会话历史和 Java 过滤后的候选商品池。
 */
@Service
public class AssistantContextService {

    private static final Pattern MESSAGE_BUDGET_MAX_PATTERN = Pattern.compile(
            "(?:预算\\s*)?(\\d{2,5})\\s*(?:以内|以下|内)|不超过\\s*(\\d{2,5})"
    );

    private final UserProfileService userProfileService;
    private final ProductCatalogService productCatalogService;
    private final ConversationService conversationService;

    public AssistantContextService(
            UserProfileService userProfileService,
            ProductCatalogService productCatalogService,
            ConversationService conversationService
    ) {
        this.userProfileService = userProfileService;
        this.productCatalogService = productCatalogService;
        this.conversationService = conversationService;
    }

    public AssistantContext buildContext(Long userId, String threadId, AssistantChatRequest request) {
        // Java 只做确定性的候选过滤，个性化解释和最终推荐话术仍由 Python AI 服务生成。
        RecommendationCandidateQuery query = new RecommendationCandidateQuery(
                request.category(),
                request.style(),
                request.season(),
                request.material(),
                request.fit(),
                resolveBudgetMax(request)
        );
        return new AssistantContext(
                userProfileService.getProfile(userId),
                userProfileService.getBodyData(userId),
                userProfileService.getPreferences(userId),
                conversationService.getMessages(userId, threadId),
                productCatalogService.findRecommendationCandidates(query)
        );
    }

    private Integer resolveBudgetMax(AssistantChatRequest request) {
        if (request.budgetMax() != null) {
            return request.budgetMax();
        }
        if (request.message() == null || request.message().isBlank()) {
            return null;
        }
        Matcher matcher = MESSAGE_BUDGET_MAX_PATTERN.matcher(request.message());
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return value == null ? null : Integer.valueOf(value);
    }
}
