package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintStrength;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    void effectiveDemandSeparatesHardAndSoftConstraints() {
        EffectiveDemand demand = EffectiveDemand.v3(
                "日常休闲", "OUTFIT_ADVICE", List.of("OUTFIT_PLAN", "PRODUCT_SELECTION"),
                List.of(hardSeason("SUMMER")), List.of(softStyle("CASUAL")), null);

        assertThat(demand.hardFilters()).extracting(IntentConstraint::strength)
                .containsOnly(ConstraintStrength.HARD);
        assertThat(demand.softPreferences()).extracting(IntentConstraint::strength)
                .containsOnly(ConstraintStrength.SOFT);
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
}
