package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintConflictResult;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintConflictStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports hard conflicts and uncommon explicit combinations without mutating effective demand.
 * Cross-turn scalar disagreements are marked as priority-resolved; same-turn disagreements remain unresolved.
 */
public class ConstraintConflictValidator {

    private static final List<String> SCALAR_FIELDS =
            List.of("targetGender", "category", "season", "budgetMax");

    /**
     * Evaluates observable effective constraints, preserving all values for later clarification or recommendation logic.
     *
     * @param demand resolved effective demand
     * @return status plus the minimal affected-field diagnosis
     */
    public ConstraintConflictResult validate(EffectiveDemand demand) {
        if (demand == null) {
            throw new IllegalArgumentException("effective demand is required");
        }
        Set<String> unresolved = new LinkedHashSet<>();
        Set<String> priorityResolved = new LinkedHashSet<>();
        for (String field : SCALAR_FIELDS) {
            List<IntentConstraint> constraints = demand.hardFilters().stream()
                    .filter(item -> item.field().equals(field) && item.operator() == ConstraintOperator.EQUALS)
                    .toList();
            if (constraints.size() < 2) {
                continue;
            }
            ConflictKind conflictKind = classifyEqualsConflicts(constraints);
            if (conflictKind == ConflictKind.UNRESOLVED) {
                unresolved.add(field);
            } else if (conflictKind == ConflictKind.CROSS_TURN) {
                priorityResolved.add(field);
            }
        }
        if (!unresolved.isEmpty()) {
            return new ConstraintConflictResult(ConstraintConflictStatus.UNRESOLVED_HARD_CONFLICT,
                    List.copyOf(unresolved), "same-turn hard scalar values disagree");
        }
        if (!priorityResolved.isEmpty()) {
            return new ConstraintConflictResult(ConstraintConflictStatus.RESOLVED_BY_PRIORITY,
                    List.copyOf(priorityResolved), "cross-turn scalar values are resolved by priority");
        }
        if (isExplicitSummerWarmth(demand)) {
            return new ConstraintConflictResult(ConstraintConflictStatus.VALID_UNCOMMON_COMBINATION,
                    List.of("season", "thermal"), "explicit summer warmth is uncommon but valid");
        }
        return new ConstraintConflictResult(ConstraintConflictStatus.VALID, List.of(), "");
    }

    private boolean isExplicitSummerWarmth(EffectiveDemand demand) {
        boolean summer = demand.hardFilters().stream()
                .anyMatch(item -> item.field().equals("season") && item.values().contains("SUMMER"));
        boolean explicitWarm = java.util.stream.Stream.concat(
                        demand.hardFilters().stream(), demand.softPreferences().stream())
                .anyMatch(item -> item.field().equals("thermal")
                        && item.origin() == ConstraintOrigin.USER_EXPLICIT
                        && item.values().contains("WARM"));
        return summer && explicitWarm;
    }

    private ConflictKind classifyEqualsConflicts(List<IntentConstraint> constraints) {
        boolean crossTurnConflict = false;
        for (int left = 0; left < constraints.size(); left++) {
            for (int right = left + 1; right < constraints.size(); right++) {
                IntentConstraint first = constraints.get(left);
                IntentConstraint second = constraints.get(right);
                if (first.values().stream().anyMatch(second.values()::contains)) {
                    continue;
                }
                if (isBlank(first.originTurnId()) || isBlank(second.originTurnId())
                        || Objects.equals(first.originTurnId(), second.originTurnId())) {
                    return ConflictKind.UNRESOLVED;
                }
                crossTurnConflict = true;
            }
        }
        return crossTurnConflict ? ConflictKind.CROSS_TURN : ConflictKind.NONE;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private enum ConflictKind {
        NONE,
        CROSS_TURN,
        UNRESOLVED
    }
}
