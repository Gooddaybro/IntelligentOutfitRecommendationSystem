package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PendingClarification;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintStrength;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DerivedConstraintResolver;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.IntentConstraintMerger;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentStateService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentResolver;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.LegacyDemandIntentAdapter;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.TurnIntentAdapter;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationDemandStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationDemandStateStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemandIntentStateServiceTests {

    @Test
    void oldSnapshotJsonDefaultsLifecycleDiagnosticToFalse() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        EffectiveDemand effective = new LegacyDemandIntentAdapter().adapt(DemandIntent.empty("legacy"));
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(
                new DemandIntentStateSnapshot(effective, null));
        json.remove("staleDerivedConstraintRemoved");

        DemandIntentStateSnapshot restored = mapper.treeToValue(json, DemandIntentStateSnapshot.class);

        assertThat(restored.staleDerivedConstraintRemoved()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void v3StateWriteReadRoundTripPreservesRawQuery() throws Exception {
        ConversationDemandStateStore store = mock(ConversationDemandStateStore.class);
        AtomicReference<ConversationDemandStateSnapshot> persisted = new AtomicReference<>();
        when(store.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    ConversationDemandStateSnapshot next = mutation.apply(new ConversationDemandStateSnapshot(
                            invocation.getArgument(6), null));
                    persisted.set(next);
                    return next;
                });
        when(store.read(1L, "thread-raw-query")).thenAnswer(invocation -> persisted.get());
        DemandIntentStateService service = new DemandIntentStateService(store);
        String rawQuery = "夏天通勤怎么穿";
        DemandIntentPatch patch = new DemandIntentPatch(
                "merge", rawQuery, null, List.of(), null, false, null, "summer",
                List.of(), List.of(), List.of(), null, List.of(), null, false);

        service.applyResolution(1L, "thread-raw-query", "turn-raw-query", null,
                patch, null, null, DemandIntent.empty(rawQuery));

        assertThat(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(persisted.get().effectiveIntentJson()).path("rawQuery").asText())
                .isEqualTo(rawQuery);
        assertThat(service.read(1L, "thread-raw-query").effectiveDemand().rawQuery()).isEqualTo(rawQuery);
    }

    @SuppressWarnings("unchecked")
    @Test
    void consecutiveParserPatchesReplaceScalarsAndExpireWinterWarmth() throws Exception {
        ConversationDemandStateStore store = mock(ConversationDemandStateStore.class);
        AtomicReference<ConversationDemandStateSnapshot> persisted = new AtomicReference<>();
        when(store.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    ConversationDemandStateSnapshot base = persisted.get();
                    if (base == null) {
                        base = new ConversationDemandStateSnapshot(invocation.getArgument(6), null);
                    }
                    ConversationDemandStateSnapshot next = mutation.apply(base);
                    persisted.set(next);
                    return next;
                });
        when(store.read(1L, "thread-multi")).thenAnswer(invocation -> persisted.get());
        DemandIntentStateService service = new DemandIntentStateService(store);
        DemandIntentResolver parser = new DemandIntentResolver();
        List<String> messages = List.of(
                "177 130 男性 冬天该怎么穿？",
                "女性呢？",
                "夏天呢？",
                "日常休闲"
        );

        DemandIntentStateSnapshot summerTransition = null;
        DemandIntentStateSnapshot ordinaryTransition = null;
        for (int index = 0; index < messages.size(); index++) {
            String message = messages.get(index);
            DemandIntent initial = DemandIntent.empty(message);
            DemandIntentStateSnapshot transition = service.applyResolution(
                    1L, "thread-multi", "turn-" + index, null,
                    parser.resolvePatch(new AssistantChatRequest(
                            "thread-multi", message, null, null, null, null, null, null, null)),
                    null, null, initial);
            if (index == 2) {
                summerTransition = transition;
            } else if (index == 3) {
                ordinaryTransition = transition;
            }
        }

        EffectiveDemand effective = service.read(1L, "thread-multi").effectiveDemand();
        assertThat(effective.value("targetGender")).contains("FEMALE");
        assertThat(effective.value("season")).contains("SUMMER");
        assertThat(effective.constraints("style")).flatExtracting(item -> item.values()).contains("CASUAL");
        assertThat(effective.constraints("thermal")).noneMatch(item ->
                item.origin() == ConstraintOrigin.SYSTEM_DERIVED && item.values().contains("WARM"));
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(persisted.get().effectiveIntentJson()).get("version").asText())
                .isEqualTo(EffectiveDemand.VERSION);
        assertThat(summerTransition.staleDerivedConstraintRemoved()).isTrue();
        assertThat(ordinaryTransition.staleDerivedConstraintRemoved()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void legacyWarmAttributeIsSoftUnprovenancedAndExpiresAfterSummerTurn() throws Exception {
        ConversationDemandStateStore store = mock(ConversationDemandStateStore.class);
        DemandIntent legacy = new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, "保暖外套",
                null, null, List.of(), List.of(), null, List.of("保暖"),
                List.of(), List.of("attributes"), BigDecimal.ONE, List.of());
        AtomicReference<ConversationDemandStateSnapshot> persisted = new AtomicReference<>(
                new ConversationDemandStateSnapshot(
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(legacy), null));
        when(store.read(1L, "thread-legacy")).thenAnswer(invocation -> persisted.get());
        when(store.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    ConversationDemandStateSnapshot next = mutation.apply(persisted.get());
                    persisted.set(next);
                    return next;
                });
        DemandIntentStateService service = new DemandIntentStateService(store);

        var migrated = service.read(1L, "thread-legacy").effectiveDemand().constraints("thermal");
        assertThat(migrated).singleElement().satisfies(item -> {
            assertThat(item.values()).containsExactly("WARM");
            assertThat(item.origin()).isEqualTo(ConstraintOrigin.LEGACY_UNPROVENANCED);
            assertThat(item.strength()).isEqualTo(ConstraintStrength.SOFT);
        });

        String summer = "夏天呢？";
        DemandIntentStateSnapshot summerTransition = service.applyResolution(
                1L, "thread-legacy", "turn-summer", null,
                new DemandIntentResolver().resolvePatch(new AssistantChatRequest(
                        "thread-legacy", summer, null, null, null, null, null, null, null)),
                null, null, DemandIntent.empty(summer));

        assertThat(service.read(1L, "thread-legacy").effectiveDemand().constraints("thermal"))
                .noneMatch(item -> item.values().contains("WARM"));
        assertThat(summerTransition.staleDerivedConstraintRemoved()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void legacySummerWarmthSurvivesTurnsThatDoNotExplicitlyReplaceSeason() throws Exception {
        ConversationDemandStateStore store = mock(ConversationDemandStateStore.class);
        DemandIntent legacy = new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, "\u590f\u5929\u4e5f\u8981\u4fdd\u6696",
                null, List.of(), null, null, "summer", List.of(), List.of(), List.of(), null,
                List.of("\u4fdd\u6696"), null, List.of("season"), List.of("attributes"),
                BigDecimal.ONE, List.of());
        AtomicReference<ConversationDemandStateSnapshot> persisted = new AtomicReference<>(
                new ConversationDemandStateSnapshot(
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(legacy), null));
        when(store.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    ConversationDemandStateSnapshot next = mutation.apply(persisted.get());
                    persisted.set(next);
                    return next;
                });
        DemandIntentStateService service = new DemandIntentStateService(store);

        var cancelled = service.applyResolution(1L, "thread-legacy-summer", "turn-cancel", null,
                "cancel_clarify", null, null, null, legacy);
        assertThat(cancelled.effectiveDemand().constraints("thermal"))
                .anyMatch(item -> item.origin() == ConstraintOrigin.LEGACY_UNPROVENANCED
                        && item.values().contains("WARM"));

        DemandIntentPatch styleOnly = new DemandIntentPatch(
                "merge", "\u65e5\u5e38\u4f11\u95f2", null, List.of(), null, false, null, null,
                List.of(), List.of("casual"), List.of(), null, List.of(), null, false);
        var merged = service.applyResolution(1L, "thread-legacy-summer", "turn-style", null,
                styleOnly, null, null, legacy);
        assertThat(merged.effectiveDemand().constraints("thermal"))
                .anyMatch(item -> item.origin() == ConstraintOrigin.LEGACY_UNPROVENANCED
                        && item.values().contains("WARM"));
    }

    @Test
    void adaptersAreCurrentTurnOnlyAndLegacyMigrationIsIdempotent() {
        DemandIntentPatch patch = new DemandIntentPatch(
                "clear", "女性日常", "OUTFIT_ADVICE", List.of("OUTFIT_PLAN"),
                "female", true, null, "summer", List.of("daily"), List.of("casual"),
                List.of(), 500, List.of(), null, true);

        var turn = new TurnIntentAdapter().adapt("turn-adapter", patch);

        assertThat(turn.scalarReplacements()).containsOnlyKeys("targetGender", "season", "budgetMax");
        assertThat(turn.scalarReplacements().get("targetGender").operator())
                .isEqualTo(ConstraintOperator.EQUALS);
        assertThat(turn.scalarReplacements().get("season").operator())
                .isEqualTo(ConstraintOperator.EQUALS);
        assertThat(turn.scalarReplacements().get("budgetMax").operator())
                .isEqualTo(ConstraintOperator.MAX);
        assertThat(turn.explicitAdditions()).extracting(item -> item.field())
                .containsExactlyInAnyOrder("scene", "style");
        assertThat(turn.explicitRemovals()).isEmpty();
        assertThat(turn.clearFields()).contains("targetGender", "subjectMeasurements");
        assertThat(turn.requestType()).isEqualTo("OUTFIT_ADVICE");
        assertThat(turn.requestedCapabilities()).containsExactly("OUTFIT_PLAN");
        assertThat(turn.rawQuery()).isEqualTo("女性日常");
        assertThat(turn.explicitAdditions()).allMatch(item ->
                item.origin() == ConstraintOrigin.USER_EXPLICIT && item.originTurnId().equals("turn-adapter"));

        LegacyDemandIntentAdapter legacyAdapter = new LegacyDemandIntentAdapter();
        DemandIntent legacy = new DemandIntent(
                DemandIntent.VERSION, "profile", "legacy", "male", null,
                List.of("daily"), List.of("casual"), null, List.of("保暖"),
                List.of("targetGender"), List.of("scene", "style", "attributes"),
                BigDecimal.ONE, List.of());
        EffectiveDemand first = legacyAdapter.adapt(legacy);
        EffectiveDemand second = legacyAdapter.adapt(legacy);

        assertThat(second).isEqualTo(first);
        assertThat(first.constraints("targetGender")).singleElement()
                .satisfies(item -> {
                    assertThat(item.strength()).isEqualTo(ConstraintStrength.HARD);
                    assertThat(item.origin()).isEqualTo(ConstraintOrigin.PROFILE);
                });
        assertThat(first.softPreferences()).allMatch(item ->
                item.origin() == ConstraintOrigin.LEGACY_UNPROVENANCED
                        && item.strength() == ConstraintStrength.SOFT);
    }

    @Test
    void summerDerivedFieldsAreOmittedFromLossyV2Projection() {
        DemandIntentPatch patch = new DemandIntentPatch(
                "merge", "\u590f\u5929\u600e\u4e48\u7a7f", "OUTFIT_ADVICE", List.of("OUTFIT_PLAN"),
                null, false, null, "summer", List.of(), List.of(), List.of(), null, List.of(), null, false);
        EffectiveDemand effective = new DerivedConstraintResolver().resolve(
                new IntentConstraintMerger().merge(null, new TurnIntentAdapter().adapt("turn-summer", patch)));

        DemandIntent projected = DemandIntentStateSnapshot.toLegacyIntent(effective);

        assertThat(projected.source()).isEqualTo("v3-projection");
        assertThat(projected.season()).isEqualTo("summer");
        assertThat(projected.hardFilters()).containsExactly("season");
        assertThat(projected.softPreferences()).isEmpty();
        assertThat(projected.attributes()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void auditPatchEnvelopePreservesDeterministicAndSemanticInputs() throws Exception {
        ConversationDemandStateStore store = mock(ConversationDemandStateStore.class);
        AtomicReference<String> auditJson = new AtomicReference<>();
        when(store.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    auditJson.set(invocation.getArgument(5));
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    return mutation.apply(new ConversationDemandStateSnapshot(invocation.getArgument(6), null));
                });
        DemandIntentPatch deterministic = new DemandIntentPatch(
                "merge", "\u5973\u6027\u4f11\u95f2", null, List.of(), "female", false, null, null,
                List.of(), List.of(), List.of(), null, List.of(), null, false);
        DemandIntentPatch semantic = new DemandIntentPatch(
                "merge", "\u5973\u6027\u4f11\u95f2", null, List.of(), null, false, null, null,
                List.of(), List.of("casual"), List.of(), null, List.of(), null, false);

        new DemandIntentStateService(store).applyResolution(
                1L, "thread-audit", "turn-audit", null, deterministic, semantic, null,
                DemandIntent.empty("\u5973\u6027\u4f11\u95f2"));

        var audit = new com.fasterxml.jackson.databind.ObjectMapper().readTree(auditJson.get());
        assertThat(audit.path("deterministicPatch").path("targetGender").asText()).isEqualTo("female");
        assertThat(audit.path("semanticPatch").path("style").get(0).asText()).isEqualTo("casual");
    }

    @SuppressWarnings("unchecked")
    @Test
    void clarificationDoesNotEnterEffectiveIntent() {
        ConversationDemandStateStore store = mock(ConversationDemandStateStore.class);
        when(store.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String initial = invocation.getArgument(6);
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    return mutation.apply(new ConversationDemandStateSnapshot(initial, null));
                });
        DemandIntentStateService service = new DemandIntentStateService(store);
        DemandIntent initial = DemandIntent.empty("她适合什么衣服");
        PendingClarification pending = new PendingClarification(
                "targetGender", "FEMALE", new BigDecimal("0.70"), "是给女性选购吗？", "她适合什么衣服");

        var result = service.applyResolution(1L, "thread", "req", null,
                new DemandIntentPatch("merge", "她适合什么衣服", null, false, null,
                        List.of(), List.of(), null, List.of()), null, pending, initial);

        assertThat(result.effectiveIntent().targetGender()).isNull();
        assertThat(result.pendingClarification()).isEqualTo(pending);
    }

    @SuppressWarnings("unchecked")
    @Test
    void cancelClarificationKeepsEffectiveSnapshotAndUsesAuditAction() {
        ConversationDemandStateStore store = mock(ConversationDemandStateStore.class);
        AtomicReference<String> action = new AtomicReference<>();
        DemandIntent previous = new com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentMerger()
                .merge(null, new DemandIntentPatch("merge", "男性外套", "male", false, "外套",
                        List.of(), List.of(), null, List.of()));
        when(store.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    action.set(invocation.getArgument(4));
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    String previousJson = new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(previous);
                    return mutation.apply(new ConversationDemandStateSnapshot(previousJson, "{}"));
                });
        DemandIntentStateService service = new DemandIntentStateService(store);

        var result = service.applyResolution(1L, "thread", "req-cancel", null,
                "cancel_clarify", null, null, null, previous);

        assertThat(action.get()).isEqualTo("cancel_clarify");
        assertThat(result.effectiveIntent().source()).isEqualTo("v3-projection");
        assertThat(result.effectiveIntent().targetGender()).isEqualTo(previous.targetGender());
        assertThat(result.effectiveIntent().category()).isEqualTo(previous.category());
        assertThat(result.effectiveIntent().hardFilters()).isEqualTo(previous.hardFilters());
        assertThat(result.pendingClarification()).isNull();
    }
}
