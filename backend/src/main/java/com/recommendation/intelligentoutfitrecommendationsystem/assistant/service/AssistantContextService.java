package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.DemandIntentParseClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DeterministicDemandParseResult;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PendingClarification;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatHistoryItem;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ValidatedDemandParseResult;
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
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
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
    private final DemandIntentStateService demandIntentStateService;
    private final DemandIntentParseClient demandIntentParseClient;
    private final DemandIntentResolver demandIntentResolver = new DemandIntentResolver();
    private final DemandIntentParseTrigger demandIntentParseTrigger = new DemandIntentParseTrigger();
    private final LlmDemandIntentValidator llmDemandIntentValidator = new LlmDemandIntentValidator();
    private final DemandIntentNormalizer demandIntentNormalizer = new DemandIntentNormalizer();
    private final LegacyDemandIntentAdapter legacyDemandIntentAdapter = new LegacyDemandIntentAdapter();

    @Autowired
    public AssistantContextService(
            UserProfileService userProfileService,
            RecommendationCandidateQueryService recommendationCandidateQueryService,
            ConversationApplicationService conversationService,
            BehaviorSummaryService behaviorSummaryService,
            DemandIntentStateService demandIntentStateService,
            DemandIntentParseClient demandIntentParseClient
    ) {
        this.userProfileService = userProfileService;
        this.recommendationCandidateQueryService = recommendationCandidateQueryService;
        this.conversationService = conversationService;
        this.behaviorSummaryService = behaviorSummaryService;
        this.demandIntentStateService = demandIntentStateService;
        this.demandIntentParseClient = demandIntentParseClient;
    }

    public AssistantContextService(
            UserProfileService userProfileService,
            RecommendationCandidateQueryService recommendationCandidateQueryService,
            ConversationApplicationService conversationService,
            BehaviorSummaryService behaviorSummaryService,
            DemandIntentStateService demandIntentStateService
    ) {
        this(userProfileService, recommendationCandidateQueryService, conversationService,
                behaviorSummaryService, demandIntentStateService, null);
    }

    public AssistantContextService(
            UserProfileService userProfileService,
            RecommendationCandidateQueryService recommendationCandidateQueryService,
            ConversationApplicationService conversationService
    ) {
        this(userProfileService, recommendationCandidateQueryService, conversationService, null, null, null);
    }

    public AssistantContextService(
            UserProfileService userProfileService,
            RecommendationCandidateQueryService recommendationCandidateQueryService,
            ConversationApplicationService conversationService,
            BehaviorSummaryService behaviorSummaryService
    ) {
        this(userProfileService, recommendationCandidateQueryService, conversationService,
                behaviorSummaryService, null, null);
    }

    public AssistantContext buildContext(Long userId, String threadId, AssistantChatRequest request) {
        UserProfileResponse profile = userProfileService.getProfile(userId);
        UserBodyDataResponse bodyData = userProfileService.getBodyData(userId);
        DemandIntent initialIntent = demandIntentResolver.resolve(request, bodyData, profile);
        List<com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse> history =
                conversationService.getMessages(userId, threadId);
        String requestId = MDC.get("requestId");
        if (!hasText(requestId)) {
            requestId = "local-" + UUID.randomUUID();
        }
        DemandIntent demandIntent = initialIntent;
        EffectiveDemand effectiveDemand = legacyDemandIntentAdapter.adapt(initialIntent);
        String clarificationQuestion = null;
        boolean staleDerivedConstraintRemoved = false;
        if (demandIntentStateService != null && demandIntentParseClient != null) {
            DemandIntentStateSnapshot current = demandIntentStateService.read(userId, threadId);
            PendingClarification pending = current == null ? null : current.pendingClarification();
            DeterministicDemandParseResult detailed = demandIntentResolver.resolveDetailed(request);
            boolean hasDeterministicChanges = !detailed.lockedSlots().isEmpty();
            DemandIntentPatch deterministicPatch = hasDeterministicChanges ? detailed.deterministicPatch() : null;
            DemandIntentPatch semanticPatch = null;
            PendingClarification nextPending = pending;
            String transitionAction = "merge";
            boolean cancelPending = pending != null
                    && (isCancellation(request.message()) || isNonShoppingInterrupt(request.message()));
            if (pending != null && isConfirmation(request.message()) && pending.candidateValue() != null) {
                transitionAction = "confirm";
                deterministicPatch = null;
                semanticPatch = patchFromPending(pending);
                nextPending = null;
            } else if (cancelPending) {
                transitionAction = "cancel_clarify";
                deterministicPatch = null;
                nextPending = null;
            } else if (pending != null && hasDeterministicChanges) {
                // A complete new demand is an explicit topic switch: merge it and discard the old question.
                transitionAction = "merge";
                nextPending = null;
            } else if (demandIntentParseTrigger.shouldParse(detailed, pending != null)) {
                var parsed = demandIntentParseClient.parse(new LlmDemandParseRequest(
                        "1.0", requestId, threadId, request.message(),
                        demandIntentNormalizer.toCanonicalDemand(
                                current == null ? initialIntent
                                        : DemandIntentStateSnapshot.toLegacyIntent(current.effectiveDemand())),
                        detailed.deterministicPatch(),
                        detailed.lockedSlots(), detailed.matchedFragments(), detailed.unresolvedText(),
                        recentHistory(history), pending));
                if (parsed.isPresent()) {
                    ValidatedDemandParseResult validated = llmDemandIntentValidator.validate(
                            parsed.get(), request.message(), Set.copyOf(detailed.lockedSlots()), pending);
                    semanticPatch = validated.patch();
                    if (validated.pendingClarification() != null) {
                        nextPending = validated.pendingClarification().withSourceRequestId(requestId);
                        transitionAction = "clarify";
                    }
                } else if (!hasDeterministicChanges && pending == null) {
                    clarificationQuestion = "\u6211\u8fd8\u4e0d\u80fd\u786e\u5b9a\u4f60\u60f3\u7b5b\u9009\u54ea\u7c7b\u7a7f\u642d\uff0c\u53ef\u4ee5\u8865\u5145\u5bf9\u8c61\u3001\u54c1\u7c7b\u6216\u573a\u666f\u5417\uff1f";
                }
            }
            DemandIntentStateSnapshot resolved = demandIntentStateService.applyResolution(
                    userId, threadId, requestId, null, transitionAction, deterministicPatch, semanticPatch,
                    nextPending, initialIntent);
            demandIntent = DemandIntentStateSnapshot.toLegacyIntent(resolved.effectiveDemand());
            effectiveDemand = resolved.effectiveDemand();
            staleDerivedConstraintRemoved = resolved.staleDerivedConstraintRemoved();
            if (resolved.pendingClarification() != null) {
                clarificationQuestion = resolved.pendingClarification().question();
            }
        } else if (demandIntentStateService != null) {
            DemandIntent persistedIntent = demandIntentStateService.apply(
                    userId, threadId, requestId, null, demandIntentResolver.resolvePatch(request), initialIntent);
            demandIntent = persistedIntent == null ? initialIntent : persistedIntent;
            effectiveDemand = legacyDemandIntentAdapter.adapt(demandIntent);
        }
        // Java SQL 消费 v3 硬约束；仅 style/material/fit 可由本次明确的 UI 过滤字段补充。
        // 软偏好完整保留在上下文中交给 Python 排序，绝不隐式转成 SQL 条件。
        RecommendationCandidateQuery query = new RecommendationCandidateQuery(
                effectiveDemand.hardValue("category").orElse(null),
                explicitCodeFilter(request.style()),
                lowerCanonical(effectiveDemand.hardValue("season").orElse(null)),
                explicitFilter(request.material()),
                explicitCodeFilter(request.fit()),
                effectiveDemand.hardInteger("budgetMax").orElse(null),
                lowerCanonical(effectiveDemand.hardValue("targetGender").orElse(null))
        );
        return new AssistantContext(
                profile,
                bodyData,
                userProfileService.getPreferences(userId),
                behaviorSummaryService == null ? null : behaviorSummaryService.getSummary(userId),
                history,
                recommendationCandidateQueryService.findCandidates(query),
                demandIntent,
                effectiveDemand,
                clarificationQuestion,
                staleDerivedConstraintRemoved
        );
    }

    private String explicitFilter(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String explicitCodeFilter(String value) {
        String filter = explicitFilter(value);
        return filter == null ? null : filter.toLowerCase(Locale.ROOT);
    }

    private String lowerCanonical(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private DemandIntentPatch patchFromPending(PendingClarification pending) {
        String gender = "targetGender".equals(pending.slot())
                ? demandIntentNormalizer.gender(String.valueOf(pending.candidateValue())) : null;
        String category = "category".equals(pending.slot())
                ? demandIntentNormalizer.category(String.valueOf(pending.candidateValue())) : null;
        Integer budget = "budgetMax".equals(pending.slot()) && pending.candidateValue() instanceof Number number
                ? number.intValue() : null;
        return new DemandIntentPatch(
                "confirm", pending.rawText(), null, List.of(), gender, false, category, null,
                List.of(), List.of(), List.of(), budget, List.of(), null, false
        );
    }

    private List<PythonChatHistoryItem> recentHistory(
            List<com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse> messages
    ) {
        List<PythonChatHistoryItem> turns = new ArrayList<>();
        String user = null;
        for (var message : messages) {
            if ("user".equalsIgnoreCase(message.role())) {
                user = message.content();
            } else if ("assistant".equalsIgnoreCase(message.role()) && user != null) {
                turns.add(new PythonChatHistoryItem(user, message.content()));
                user = null;
            }
        }
        int from = Math.max(0, turns.size() - 3);
        List<PythonChatHistoryItem> recent = new ArrayList<>(turns.subList(from, turns.size()));
        int excess = recent.stream().mapToInt(this::historyCharacters).sum() - 4000;
        for (int index = 0; index < recent.size() && excess > 0; index++) {
            PythonChatHistoryItem turn = recent.get(index);
            String trimmedUser = trimOldest(turn.userQuery(), excess);
            excess -= length(turn.userQuery()) - length(trimmedUser);
            String trimmedAssistant = trimOldest(turn.assistantAnswer(), excess);
            excess -= length(turn.assistantAnswer()) - length(trimmedAssistant);
            recent.set(index, new PythonChatHistoryItem(trimmedUser, trimmedAssistant));
        }
        return List.copyOf(recent);
    }

    private int historyCharacters(PythonChatHistoryItem turn) {
        return length(turn.userQuery()) + length(turn.assistantAnswer());
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String trimOldest(String value, int excess) {
        if (value == null || excess <= 0) {
            return value;
        }
        return value.substring(Math.min(excess, value.length()));
    }

    private boolean isConfirmation(String message) {
        return message != null && Set.of("是", "对", "确认", "可以", "没错").contains(message.trim());
    }

    private boolean isCancellation(String message) {
        return message != null && Set.of("算了", "取消", "不用了", "不需要").contains(message.trim());
    }

    private boolean isNonShoppingInterrupt(String message) {
        return containsAny(message, "\u8ba2\u5355", "\u7269\u6d41", "\u53d1\u8d27", "\u9000\u6b3e", "\u9000\u8d27", "\u552e\u540e", "\u5e93\u5b58", "\u4ef7\u683c", "\u591a\u5c11\u94b1");
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

}
