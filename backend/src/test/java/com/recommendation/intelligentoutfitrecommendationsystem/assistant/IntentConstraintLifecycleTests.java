package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintConflictStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintStrength;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.TurnIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ConstraintConflictValidator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DerivedConstraintResolver;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.IntentConstraintMerger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IntentConstraintLifecycleTests {

    private final IntentConstraintMerger merger = new IntentConstraintMerger();
    private final DerivedConstraintResolver resolver = new DerivedConstraintResolver();
    private final ConstraintConflictValidator validator = new ConstraintConflictValidator();

    @Test
    void changingWinterToSummerRemovesOnlyWinterDerivedWarmth() {
        IntentConstraint winter = hard("c-season-turn-1", "season", "WINTER", "turn-1");
        EffectiveDemand previous = demand(
                List.of(winter),
                List.of(derived("c-thermal-turn-1", "thermal", "WARM", "turn-1", winter.id()))
        );
        IntentConstraint summer = hard("c-season-turn-2", "season", "SUMMER", "turn-2");
        TurnIntent turn = turn("turn-2", Map.of("season", summer), List.of(), List.of(), Set.of());

        EffectiveDemand merged = merger.merge(previous, turn);
        EffectiveDemand resolved = resolver.resolve(merged);

        assertThat(resolved.value("season")).contains("SUMMER");
        assertThat(resolved.softPreferences()).noneMatch(item ->
                item.origin() == ConstraintOrigin.SYSTEM_DERIVED && item.values().contains("WARM"));
        assertThat(resolved.softPreferences()).anyMatch(item ->
                item.origin() == ConstraintOrigin.SYSTEM_DERIVED && item.values().contains("BREATHABLE"));
        assertThat(resolver.resolve(resolved)).isEqualTo(resolved);
    }

    @Test
    void explicitSummerWarmthSurvivesDerivedRecalculation() {
        EffectiveDemand input = demand(
                List.of(hard("c-season-turn-3", "season", "SUMMER", "turn-3")),
                List.of(soft("c-thermal-turn-3", "thermal", "WARM", "turn-3"))
        );

        EffectiveDemand resolved = resolver.resolve(input);

        assertThat(resolved.softPreferences()).anyMatch(item ->
                item.origin() == ConstraintOrigin.USER_EXPLICIT && item.values().contains("WARM"));
        assertThat(validator.validate(resolved).status())
                .isEqualTo(ConstraintConflictStatus.VALID_UNCOMMON_COMBINATION);
    }

    @Test
    void explicitConstraintWinsSemanticDeduplicationEvenWhenDerivedConstraintComesFirst() {
        IntentConstraint summer = hard("c-season-turn-3", "season", "SUMMER", "turn-3");
        IntentConstraint derivedCooling = derived(
                "c-derived-cooling", "thermal", "COOLING", "turn-3", summer.id());
        IntentConstraint explicitCooling = soft(
                "c-explicit-cooling", "thermal", "COOLING", "turn-4");
        EffectiveDemand input = demand(List.of(summer), List.of(derivedCooling, explicitCooling));

        EffectiveDemand resolved = resolver.resolve(input);

        assertThat(resolved.constraints("thermal"))
                .filteredOn(item -> item.values().contains("COOLING"))
                .containsExactly(explicitCooling);
    }

    @Test
    void hardExplicitSummerWarmthIsAnUncommonValidCombination() {
        EffectiveDemand input = demand(List.of(
                hard("c-season-turn-5", "season", "SUMMER", "turn-5"),
                hard("c-thermal-turn-5", "thermal", "WARM", "turn-5")), List.of());

        assertThat(validator.validate(input).status())
                .isEqualTo(ConstraintConflictStatus.VALID_UNCOMMON_COMBINATION);
    }

    @Test
    void currentExplicitScalarWinsOverHistoricalExplicitScalar() {
        EffectiveDemand previous = demand(
                List.of(hard("c-gender-turn-1", "targetGender", "MALE", "turn-1")), List.of());
        IntentConstraint female = hard("c-gender-turn-2", "targetGender", "FEMALE", "turn-2");

        EffectiveDemand merged = merger.merge(previous,
                turn("turn-2", Map.of("targetGender", female), List.of(), List.of(), Set.of()));

        assertThat(merged.constraints("targetGender")).containsExactly(female);
    }

    @Test
    void mergeUsesReplaceAppendAndRemovePoliciesPerListField() {
        IntentConstraint oldStyle = soft("c-style-old", "style", "CASUAL", "turn-1");
        IntentConstraint oldFit = soft("c-fit-old", "fitPreferences", "SLIM", "turn-1");
        IntentConstraint oldScene = soft("c-scene-old", "scene", "COMMUTE", "turn-1");
        IntentConstraint removedAttribute = soft("c-attribute-old", "attributes", "HOODED", "turn-1");
        IntentConstraint oldThermal = soft("c-thermal-old", "thermal", "WARM", "turn-1");
        EffectiveDemand previous = demand(List.of(),
                List.of(oldStyle, oldFit, oldScene, removedAttribute, oldThermal));
        List<IntentConstraint> additions = List.of(
                soft("c-style-new", "style", "FORMAL", "turn-2"),
                soft("c-fit-new", "fitPreferences", "RELAXED", "turn-2"),
                soft("c-scene-new", "scene", "TRAVEL", "turn-2"),
                soft("c-attribute-new", "attributes", "WATERPROOF", "turn-2"),
                soft("c-thermal-new", "thermal", "COOLING", "turn-2")
        );

        EffectiveDemand merged = merger.merge(previous,
                turn("turn-2", Map.of(), additions, List.of(removedAttribute), Set.of()));

        assertThat(values(merged, "style")).containsExactly("FORMAL");
        assertThat(values(merged, "fitPreferences")).containsExactly("RELAXED");
        assertThat(values(merged, "scene")).containsExactly("COMMUTE", "TRAVEL");
        assertThat(values(merged, "attributes")).containsExactly("WATERPROOF");
        assertThat(values(merged, "thermal")).containsExactly("WARM", "COOLING");
    }

    @Test
    void validatorDistinguishesSameTurnHardConflictFromHistoricalPriorityResolution() {
        EffectiveDemand sameTurnConflict = demand(List.of(
                hard("c-season-a", "season", "WINTER", "turn-4"),
                hard("c-season-b", "season", "SUMMER", "turn-4")), List.of());
        EffectiveDemand crossTurnConflict = demand(List.of(
                hard("c-season-old", "season", "WINTER", "turn-1"),
                hard("c-season-new", "season", "SUMMER", "turn-4")), List.of());

        assertThat(validator.validate(sameTurnConflict).status())
                .isEqualTo(ConstraintConflictStatus.UNRESOLVED_HARD_CONFLICT);
        assertThat(validator.validate(sameTurnConflict).conflictingFields()).containsExactly("season");
        assertThat(validator.validate(crossTurnConflict).status())
                .isEqualTo(ConstraintConflictStatus.RESOLVED_BY_PRIORITY);
    }

    @Test
    void oneMultiValueHardConstraintIsNotMistakenForTwoConflictingConstraints() {
        IntentConstraint multiValueSeason = new IntentConstraint(
                "c-season-multi", "season", ConstraintOperator.EQUALS, List.of("SUMMER", "WINTER"),
                ConstraintStrength.HARD, ConstraintOrigin.USER_EXPLICIT,
                "turn-6", null, "ACTIVE_DEMAND", null);

        assertThat(validator.validate(demand(List.of(multiValueSeason), List.of())).status())
                .isEqualTo(ConstraintConflictStatus.VALID);
    }

    private List<String> values(EffectiveDemand demand, String field) {
        return demand.constraints(field).stream().flatMap(item -> item.values().stream()).toList();
    }

    private EffectiveDemand demand(List<IntentConstraint> hard, List<IntentConstraint> soft) {
        return EffectiveDemand.v3("previous", "OUTFIT_ADVICE", List.of("OUTFIT_PLAN"), hard, soft, null);
    }

    private TurnIntent turn(
            String turnId,
            Map<String, IntentConstraint> scalars,
            List<IntentConstraint> additions,
            List<IntentConstraint> removals,
            Set<String> clearFields
    ) {
        return new TurnIntent(turnId, "current", scalars, additions, removals, clearFields,
                null, List.of(), null);
    }

    private IntentConstraint hard(String id, String field, String value, String turnId) {
        return new IntentConstraint(id, field, ConstraintOperator.EQUALS, List.of(value),
                ConstraintStrength.HARD, ConstraintOrigin.USER_EXPLICIT,
                turnId, null, "ACTIVE_DEMAND", null);
    }

    private IntentConstraint soft(String id, String field, String value, String turnId) {
        return new IntentConstraint(id, field, ConstraintOperator.CONTAINS, List.of(value),
                ConstraintStrength.SOFT, ConstraintOrigin.USER_EXPLICIT,
                turnId, null, "ACTIVE_DEMAND", null);
    }

    private IntentConstraint derived(
            String id,
            String field,
            String value,
            String turnId,
            String parentId
    ) {
        return new IntentConstraint(id, field, ConstraintOperator.CONTAINS, List.of(value),
                ConstraintStrength.SOFT, ConstraintOrigin.SYSTEM_DERIVED,
                turnId, parentId, "ACTIVE_DEMAND", null);
    }
}
