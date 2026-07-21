package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable intent condition whose provenance keeps explicit, profile and derived facts separable.
 * Hard conditions belong to filtering and therefore cannot carry a ranking weight.
 */
public record IntentConstraint(
        String id,
        String field,
        ConstraintOperator operator,
        List<String> values,
        ConstraintStrength strength,
        ConstraintOrigin origin,
        String originTurnId,
        String derivedFromConstraintId,
        String scope,
        BigDecimal weight
) {
    public IntentConstraint {
        values = values == null ? List.of() : List.copyOf(values);
        if (id == null || id.isBlank() || field == null || field.isBlank() || values.isEmpty()) {
            throw new IllegalArgumentException("constraint id, field and values are required");
        }
        if (operator == null || strength == null || origin == null) {
            throw new IllegalArgumentException("constraint operator, strength and origin are required");
        }
        if (strength == ConstraintStrength.HARD && weight != null) {
            throw new IllegalArgumentException("hard constraints cannot carry ranking weight");
        }
        if (origin == ConstraintOrigin.SYSTEM_DERIVED
                && (derivedFromConstraintId == null || derivedFromConstraintId.isBlank())) {
            throw new IllegalArgumentException("derived constraints require a parent constraint id");
        }
        if (origin != ConstraintOrigin.SYSTEM_DERIVED && derivedFromConstraintId != null) {
            throw new IllegalArgumentException("non-derived constraints cannot carry a parent constraint id");
        }
    }

    /** Returns whether this condition must be removed or recalculated with its parent condition. */
    public boolean isDerived() {
        return origin == ConstraintOrigin.SYSTEM_DERIVED;
    }
}
