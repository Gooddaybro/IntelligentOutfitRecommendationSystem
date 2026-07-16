package com.recommendation.intelligentoutfitrecommendationsystem.conversation.model;

import lombok.Data;

import java.time.LocalDateTime;

/** Current effective demand snapshot for one chat session. */
@Data
public class ChatDemandState {
    private Long sessionId;
    private Long stateVersion;
    private String effectiveIntentJson;
    private String pendingClarificationJson;
    private String lastRequestId;
    private LocalDateTime updatedAt;
}
