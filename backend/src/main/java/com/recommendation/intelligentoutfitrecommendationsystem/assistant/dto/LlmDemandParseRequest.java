package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;
import java.util.Map;

/** Java-owned context sent to the isolated Python semantic parser. */
public record LlmDemandParseRequest(
        String schemaVersion,
        String requestId,
        String sessionId,
        String currentMessage,
        Map<String, Object> currentDemand,
        DemandIntentPatch deterministicPatch,
        List<String> lockedSlots,
        List<String> matchedFragments,
        String unresolvedText,
        List<PythonChatHistoryItem> recentHistory,
        PendingClarification pendingClarification
) {
    public LlmDemandParseRequest {
        schemaVersion = schemaVersion == null ? "1.0" : schemaVersion;
        currentDemand = currentDemand == null ? Map.of() : Map.copyOf(currentDemand);
        lockedSlots = lockedSlots == null ? List.of() : List.copyOf(lockedSlots);
        matchedFragments = matchedFragments == null ? List.of() : List.copyOf(matchedFragments);
        recentHistory = recentHistory == null ? List.of() : List.copyOf(recentHistory);
    }

    public LlmDemandParseRequest(
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
        this("1.0", requestId, sessionId, currentMessage, Map.of(), deterministicPatch,
                lockedSlots, matchedFragments, unresolvedText, recentHistory, pendingClarification);
    }
}
