package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintStrength;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.TurnIntent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentConstraintTests {

    @Test
    void derivedConstraintRetainsItsParentIdentity() {
        IntentConstraint constraint = new IntentConstraint(
                "c-thermal-turn-1", "thermal", ConstraintOperator.EQUALS, List.of("WARM"),
                ConstraintStrength.SOFT, ConstraintOrigin.SYSTEM_DERIVED,
                "turn-1", "c-season-turn-1", "ACTIVE_DEMAND", null);

        assertThat(constraint.isDerived()).isTrue();
        assertThat(constraint.derivedFromConstraintId()).isEqualTo("c-season-turn-1");
    }

    @Test
    void ordinaryObjectMapperDoesNotSerializeDerivedConvenienceProperty() throws Exception {
        IntentConstraint constraint = new IntentConstraint(
                "c-thermal-turn-1", "thermal", ConstraintOperator.EQUALS, List.of("WARM"),
                ConstraintStrength.SOFT, ConstraintOrigin.SYSTEM_DERIVED,
                "turn-1", "c-season-turn-1", "ACTIVE_DEMAND", null);

        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(constraint));

        assertThat(json.has("derived")).isFalse();
        assertThat(json.path("derivedFromConstraintId").asText()).isEqualTo("c-season-turn-1");
    }

    @Test
    void effectiveDemandSeparatesHardAndSoftConstraints() {
        EffectiveDemand demand = EffectiveDemand.v3(
                "日常休闲", "OUTFIT_ADVICE", List.of("OUTFIT_PLAN", "PRODUCT_SELECTION"),
                List.of(hardSeason("SUMMER")), List.of(softStyle("CASUAL")), null);

        assertThat(demand.hardFilters()).extracting(IntentConstraint::strength)
                .containsOnly(ConstraintStrength.HARD);
        assertThat(demand.softPreferences()).extracting(IntentConstraint::strength)
                .containsOnly(ConstraintStrength.SOFT);
    }

    @Test
    void constraintRequiresItsCoreEnumMetadata() {
        assertThatThrownBy(() -> constraint(null, ConstraintStrength.SOFT, ConstraintOrigin.USER_EXPLICIT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> constraint(ConstraintOperator.EQUALS, null, ConstraintOrigin.USER_EXPLICIT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> constraint(ConstraintOperator.EQUALS, ConstraintStrength.SOFT, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constraintEnforcesDerivedParentLinkage() {
        assertThatThrownBy(() -> constraint(
                ConstraintOperator.EQUALS, ConstraintStrength.SOFT, ConstraintOrigin.SYSTEM_DERIVED, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> constraint(
                ConstraintOperator.EQUALS, ConstraintStrength.SOFT, ConstraintOrigin.USER_EXPLICIT, "c-parent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void effectiveDemandRejectsConstraintsInTheWrongPartition() {
        assertThatThrownBy(() -> EffectiveDemand.v3(
                "日常休闲", "OUTFIT_ADVICE", List.of(),
                List.of(softStyle("CASUAL")), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EffectiveDemand.v3(
                "日常休闲", "OUTFIT_ADVICE", List.of(),
                List.of(), List.of(hardSeason("SUMMER")), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constraintValuesAreDefensivelyCopiedAndUnmodifiable() {
        List<String> values = new ArrayList<>(List.of("CASUAL"));
        IntentConstraint constraint = new IntentConstraint(
                "c-style-turn-4", "style", ConstraintOperator.CONTAINS, values,
                ConstraintStrength.SOFT, ConstraintOrigin.USER_EXPLICIT,
                "turn-4", null, "ACTIVE_DEMAND", null);

        values.add("FORMAL");

        assertThat(constraint.values()).containsExactly("CASUAL");
        assertThatThrownBy(() -> constraint.values().add("FORMAL"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void turnIntentRejectsScalarKeysThatDisagreeWithConstraintFields() {
        IntentConstraint season = hardSeason("SUMMER");

        assertThatThrownBy(() -> new TurnIntent(
                "turn-4", "夏季穿搭", Map.of("style", season),
                List.of(), List.of(), null, "OUTFIT_ADVICE", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private IntentConstraint hardSeason(String value) {
        return new IntentConstraint(
                "c-season-turn-4", "season", ConstraintOperator.EQUALS, List.of(value),
                ConstraintStrength.HARD, ConstraintOrigin.USER_EXPLICIT,
                "turn-4", null, "ACTIVE_DEMAND", null);
    }

    private IntentConstraint softStyle(String value) {
        return new IntentConstraint(
                "c-style-turn-4", "style", ConstraintOperator.CONTAINS, List.of(value),
                ConstraintStrength.SOFT, ConstraintOrigin.USER_EXPLICIT,
                "turn-4", null, "ACTIVE_DEMAND", null);
    }

    private IntentConstraint constraint(
            ConstraintOperator operator,
            ConstraintStrength strength,
            ConstraintOrigin origin,
            String derivedFromConstraintId
    ) {
        return new IntentConstraint(
                "c-style-turn-4", "style", operator, List.of("CASUAL"), strength, origin,
                "turn-4", derivedFromConstraintId, "ACTIVE_DEMAND", null);
    }
}
