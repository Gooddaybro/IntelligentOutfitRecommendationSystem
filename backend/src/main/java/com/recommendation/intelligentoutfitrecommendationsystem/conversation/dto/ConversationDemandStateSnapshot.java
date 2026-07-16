package com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto;

/** Conversation-owned JSON state boundary shared without importing assistant domain types. */
public record ConversationDemandStateSnapshot(
        String effectiveIntentJson,
        String pendingClarificationJson
) {
}
