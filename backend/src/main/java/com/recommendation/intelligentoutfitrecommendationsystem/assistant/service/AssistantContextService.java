package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorSummaryService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 组装 Java 业务上下文给 Python AI 服务。
 *
 * Python 不直接读取 Java 数据库；它拿到的是用户画像、会话历史和 Java 过滤后的候选商品池。
 */
@Service
public class AssistantContextService {

    private static final String MALE = "male";
    private static final String FEMALE = "female";
    private static final String[] MALE_SIGNALS = {"男", "男生", "男性", "男士", "男款", "男朋友", "爸爸", "男友"};
    private static final String[] FEMALE_SIGNALS = {"女", "女生", "女性", "女士", "女款", "女朋友", "妈妈", "女友"};
    private static final Pattern MESSAGE_BUDGET_MAX_PATTERN = Pattern.compile(
            "(?:预算\\s*)?(\\d{2,5})\\s*(?:以内|以下|内)|不超过\\s*(\\d{2,5})"
    );

    private final UserProfileService userProfileService;
    private final ProductCatalogService productCatalogService;
    private final ConversationService conversationService;
    private final BehaviorSummaryService behaviorSummaryService;

    public AssistantContextService(
            UserProfileService userProfileService,
            ProductCatalogService productCatalogService,
            ConversationService conversationService,
            BehaviorSummaryService behaviorSummaryService
    ) {
        this.userProfileService = userProfileService;
        this.productCatalogService = productCatalogService;
        this.conversationService = conversationService;
        this.behaviorSummaryService = behaviorSummaryService;
    }

    public AssistantContext buildContext(Long userId, String threadId, AssistantChatRequest request) {
        UserProfileResponse profile = userProfileService.getProfile(userId);
        UserBodyDataResponse bodyData = userProfileService.getBodyData(userId);
        // Java 只做确定性的候选过滤，个性化解释和最终推荐话术仍由 Python AI 服务生成。
        RecommendationCandidateQuery query = new RecommendationCandidateQuery(
                request.category(),
                request.style(),
                request.season(),
                request.material(),
                request.fit(),
                resolveBudgetMax(request),
                resolveTargetGender(request, bodyData, profile)
        );
        return new AssistantContext(
                profile,
                bodyData,
                userProfileService.getPreferences(userId),
                behaviorSummaryService.getSummary(userId),
                conversationService.getMessages(userId, threadId),
                productCatalogService.findRecommendationCandidates(query)
        );
    }

    private String resolveTargetGender(
            AssistantChatRequest request,
            UserBodyDataResponse bodyData,
            UserProfileResponse profile
    ) {
        String messageGender = genderFromMessage(request.message());
        if (messageGender != null) {
            return messageGender;
        }
        String requestGender = normalizeGender(request.gender());
        if (requestGender != null) {
            return requestGender;
        }
        String bodyGender = normalizeGender(bodyData == null ? null : bodyData.gender());
        if (bodyGender != null) {
            return bodyGender;
        }
        return normalizeGender(profile == null ? null : profile.gender());
    }

    private String genderFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        boolean hasMale = containsAny(message, MALE_SIGNALS);
        boolean hasFemale = containsAny(message, FEMALE_SIGNALS);
        if (hasMale == hasFemale) {
            return null;
        }
        return hasMale ? MALE : FEMALE;
    }

    private boolean containsAny(String text, String[] signals) {
        for (String signal : signals) {
            if (text.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        String normalized = gender.trim().toLowerCase(Locale.ROOT);
        if (MALE.equals(normalized) || FEMALE.equals(normalized)) {
            return normalized;
        }
        if ("男".equals(normalized) || "男生".equals(normalized) || "男性".equals(normalized)) {
            return MALE;
        }
        if ("女".equals(normalized) || "女生".equals(normalized) || "女性".equals(normalized)) {
            return FEMALE;
        }
        return null;
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
