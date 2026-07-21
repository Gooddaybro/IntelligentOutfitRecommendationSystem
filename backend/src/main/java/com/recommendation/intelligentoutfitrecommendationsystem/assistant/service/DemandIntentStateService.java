package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintConflictResult;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PendingClarification;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.TurnIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationDemandStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationDemandStateStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Owns the assistant demand-state transition and the compatibility migration from persisted v2 JSON.
 * The unchanged conversation columns store v3 effective JSON separately from a clarification/conflict workflow envelope.
 */
@Service
public class DemandIntentStateService {

    private final ConversationDemandStateStore stateStore;
    private final ObjectMapper objectMapper = createObjectMapper();
    private final TurnIntentAdapter turnAdapter = new TurnIntentAdapter();
    private final LegacyDemandIntentAdapter legacyAdapter = new LegacyDemandIntentAdapter();
    private final IntentConstraintMerger merger = new IntentConstraintMerger();
    private final DerivedConstraintResolver resolver = new DerivedConstraintResolver();
    private final ConstraintConflictValidator conflictValidator = new ConstraintConflictValidator();

    public DemandIntentStateService(ConversationDemandStateStore stateStore) {
        this.stateStore = stateStore;
    }

    /** Applies the established single-patch API while persisting only v3 state internally. */
    @Transactional
    public DemandIntent apply(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            DemandIntentPatch patch,
            DemandIntent initialIntent
    ) {
        DemandIntentStateSnapshot snapshot = transition(userId, threadId, requestId, messageId,
                patch.action(), patch, null, null, initialIntent);
        return snapshot.effectiveIntent();
    }

    /** Reads either v3 JSON or an unmigrated v2 row and exposes one typed state shape. */
    public DemandIntentStateSnapshot read(Long userId, String threadId) {
        ConversationDemandStateSnapshot stored = stateStore.read(userId, threadId);
        return stored == null ? null : snapshot(stored);
    }

    @Transactional
    public DemandIntentStateSnapshot applyResolution(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            DemandIntentPatch deterministicPatch,
            DemandIntentPatch semanticPatch,
            PendingClarification pending,
            DemandIntent initialIntent
    ) {
        String action = pending == null ? "merge" : "clarify";
        return applyResolution(userId, threadId, requestId, messageId, action,
                deterministicPatch, semanticPatch, pending, initialIntent);
    }

    /** Atomically applies one explicit transition in adapter, merger, resolver and validation order. */
    @Transactional
    public DemandIntentStateSnapshot applyResolution(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            String action,
            DemandIntentPatch deterministicPatch,
            DemandIntentPatch semanticPatch,
            PendingClarification pending,
            DemandIntent initialIntent
    ) {
        if (!Set.of("merge", "clarify", "confirm", "cancel_clarify").contains(action)) {
            throw new IllegalArgumentException("unsupported demand transition action");
        }
        return transition(userId, threadId, requestId, messageId, action,
                deterministicPatch, semanticPatch, pending, initialIntent);
    }

    private DemandIntentStateSnapshot transition(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            String action,
            DemandIntentPatch deterministicPatch,
            DemandIntentPatch semanticPatch,
            PendingClarification pending,
            DemandIntent initialIntent
    ) {
        String turnId = turnId(requestId, messageId);
        EffectiveDemand initial = legacyAdapter.adapt(initialIntent);
        ConversationDemandStateSnapshot stored = stateStore.transition(
                userId, threadId, requestId, messageId, action,
                writeJson(semanticPatch == null ? deterministicPatch : semanticPatch),
                writeJson(initial),
                current -> {
                    EffectiveDemand effective = readEffective(current.effectiveIntentJson());
                    if (!"cancel_clarify".equals(action)) {
                        if (deterministicPatch != null) {
                            effective = mergeTurn(resetBase(effective, deterministicPatch),
                                    turnAdapter.adapt(turnId, deterministicPatch));
                        }
                        if (semanticPatch != null) {
                            effective = mergeTurn(resetBase(effective, semanticPatch),
                                    turnAdapter.adapt(turnId, semanticPatch));
                        }
                    }
                    EffectiveDemand resolved = resolver.resolve(effective);
                    ConstraintConflictResult conflict = conflictValidator.validate(resolved, turnId);
                    PendingClarification nextPending = "cancel_clarify".equals(action) ? null : pending;
                    return new ConversationDemandStateSnapshot(
                            writeJson(resolved), writeJson(new WorkflowState(nextPending, conflict)));
                });
        return snapshot(stored);
    }

    private EffectiveDemand resetBase(EffectiveDemand current, DemandIntentPatch patch) {
        return "reset".equalsIgnoreCase(patch.action())
                ? EffectiveDemand.v3("", null, List.of(), List.of(), List.of(), null) : current;
    }

    private EffectiveDemand mergeTurn(EffectiveDemand previous, TurnIntent turn) {
        EffectiveDemand merged = merger.merge(previous, turn);
        if (!turn.scalarReplacements().containsKey("season")
                || !turn.scalarReplacements().get("season").values().contains("SUMMER")) {
            return merged;
        }
        var retained = merged.softPreferences().stream()
                .filter(item -> !(item.origin() == ConstraintOrigin.LEGACY_UNPROVENANCED
                        && item.field().equals("thermal") && item.values().contains("WARM")))
                .toList();
        if (retained.size() == merged.softPreferences().size()) {
            return merged;
        }
        return merged.withSoftPreferences(retained);
    }

    private DemandIntentStateSnapshot snapshot(ConversationDemandStateSnapshot stored) {
        WorkflowState workflow = readWorkflow(stored.pendingClarificationJson());
        EffectiveDemand effective = readEffective(stored.effectiveIntentJson());
        ConstraintConflictResult conflict = workflow.conflictResult() == null
                ? conflictValidator.validate(effective) : workflow.conflictResult();
        return new DemandIntentStateSnapshot(effective, workflow.pendingClarification(), conflict);
    }

    private WorkflowState readWorkflow(String json) {
        if (json == null || json.isBlank()) {
            return new WorkflowState(null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("pendingClarification") || root.has("conflictResult")) {
                return objectMapper.readValue(json, WorkflowState.class);
            }
            return new WorkflowState(objectMapper.readValue(json, PendingClarification.class), null);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("invalid stored demand workflow", exception);
        }
    }

    private EffectiveDemand readEffective(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root != null && EffectiveDemand.VERSION.equals(root.path("version").asText())) {
                return objectMapper.treeToValue(root, EffectiveDemand.class);
            }
            return legacyAdapter.adapt(objectMapper.treeToValue(root, DemandIntent.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("invalid stored demand intent", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize demand state", exception);
        }
    }

    private String turnId(String requestId, Long messageId) {
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return messageId == null ? "unidentified-current-turn" : "message-" + messageId;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(IntentConstraint.class, IntentConstraintJsonMixin.class);
        return mapper;
    }

    private abstract static class IntentConstraintJsonMixin {
        @JsonIgnore
        abstract boolean isDerived();
    }

    private record WorkflowState(
            PendingClarification pendingClarification,
            ConstraintConflictResult conflictResult
    ) {
    }
}
