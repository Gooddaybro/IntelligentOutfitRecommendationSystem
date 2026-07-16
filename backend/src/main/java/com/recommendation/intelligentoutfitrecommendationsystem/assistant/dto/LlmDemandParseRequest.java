package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/** Java-owned context sent to the isolated Python semantic parser. */
public record LlmDemandParseRequest(
        String requestId,
        String sessionId,
        String currentMessage,
        DemandIntentPatch deterministicPatch,
        List<String> lockedSlots,
        List<String> matchedFragments,
        String unresolvedText,
        List<PythonChatHistoryItem> recentHistory,
        PendingClarification pendingClarification
) {
    public LlmDemandParseRequest {
        lockedSlots = lockedSlots == null ? List.of() : List.copyOf(lockedSlots);
        matchedFragments = matchedFragments == null ? List.of() : List.copyOf(matchedFragments);
        recentHistory = recentHistory == null ? List.of() : List.copyOf(recentHistory);
    }
}
