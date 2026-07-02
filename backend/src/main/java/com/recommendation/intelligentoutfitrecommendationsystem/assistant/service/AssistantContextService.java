package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 组装 Java 业务上下文给 Python AI 服务。
 *
 * Python 不直接读取 Java 数据库；它拿到的是用户画像、会话历史和 Java 过滤后的候选商品池。
 */
@Service
public class AssistantContextService {

    private final UserProfileService userProfileService;
    private final ProductCatalogService productCatalogService;
    private final ConversationService conversationService;
    private final DemandIntentResolver demandIntentResolver;

    @Autowired
    public AssistantContextService(
            UserProfileService userProfileService,
            ProductCatalogService productCatalogService,
            ConversationService conversationService,
            DemandIntentResolver demandIntentResolver
    ) {
        this.userProfileService = userProfileService;
        this.productCatalogService = productCatalogService;
        this.conversationService = conversationService;
        this.demandIntentResolver = demandIntentResolver;
    }

    public AssistantContextService(
            UserProfileService userProfileService,
            ProductCatalogService productCatalogService,
            ConversationService conversationService
    ) {
        this(userProfileService, productCatalogService, conversationService, new DemandIntentResolver());
    }

    public AssistantContext buildContext(Long userId, String threadId, AssistantChatRequest request) {
        UserProfileResponse profile = userProfileService.getProfile(userId);
        UserBodyDataResponse bodyData = userProfileService.getBodyData(userId);
        DemandIntent demandIntent = demandIntentResolver.resolve(request, bodyData, profile);
        // Java 只做确定性的候选过滤，个性化解释和最终推荐话术仍由 Python AI 服务生成。
        RecommendationCandidateQuery query = new RecommendationCandidateQuery(
                demandIntent.category(),
                firstValue(demandIntent.style()),
                request.season(),
                request.material(),
                request.fit(),
                demandIntent.budgetMax(),
                demandIntent.targetGender()
        );
        return new AssistantContext(
                profile,
                bodyData,
                userProfileService.getPreferences(userId),
                conversationService.getMessages(userId, threadId),
                productCatalogService.findRecommendationCandidates(query),
                demandIntent
        );
    }

    private String firstValue(java.util.List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
