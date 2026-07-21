package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintConflictResult;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintConflictStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports hard conflicts and uncommon explicit combinations without mutating effective demand.
 * Cross-turn scalar disagreements are priority-resolved only when the caller identifies the winning current turn.
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
        return validateInternal(demand, null);
    }

    /**
     * Evaluates conflicts with an explicit current-turn identity, allowing a satisfiable current value set to win over
     * incompatible historical scalar values. A blank or unmatched identity falls back to conservative validation.
     *
     * @param demand resolved effective demand
     * @param currentTurnId identity of the turn whose explicit scalar constraints have priority
     * @return status plus the minimal affected-field diagnosis
     */
    public ConstraintConflictResult validate(EffectiveDemand demand, String currentTurnId) {
        return validateInternal(demand, currentTurnId);
    }

    private ConstraintConflictResult validateInternal(EffectiveDemand demand, String currentTurnId) {
        if (demand == null) {
            throw new IllegalArgumentException("effective demand is required");
        }
        Set<String> unresolved = new LinkedHashSet<>();
        Set<String> priorityResolved = new LinkedHashSet<>();
        boolean currentTurnSetIsUnsatisfiable = false;
        for (String field : SCALAR_FIELDS) {
            List<IntentConstraint> constraints = demand.hardFilters().stream()
                    .filter(item -> item.field().equals(field) && item.operator() == ConstraintOperator.EQUALS)
                    .toList();
            if (constraints.size() < 2) {
                continue;
            }
            ConflictKind conflictKind = classifyEqualsConflicts(constraints, currentTurnId);
            if (conflictKind == ConflictKind.UNRESOLVED_CURRENT_TURN) {
                unresolved.add(field);
                currentTurnSetIsUnsatisfiable = true;
            } else if (conflictKind == ConflictKind.UNRESOLVED_NO_WINNER) {
                unresolved.add(field);
            } else if (conflictKind == ConflictKind.CROSS_TURN) {
                priorityResolved.add(field);
            }
        }
        if (!unresolved.isEmpty()) {
            String reason = currentTurnSetIsUnsatisfiable
                    ? "current-turn hard scalar values have no common value"
                    : "current turn is missing or does not identify a trustworthy priority winner";
            return new ConstraintConflictResult(ConstraintConflictStatus.UNRESOLVED_HARD_CONFLICT,
                    List.copyOf(unresolved), reason);
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

    private ConflictKind classifyEqualsConflicts(
            List<IntentConstraint> constraints,
            String currentTurnId
    ) {
        if (!intersection(constraints).isEmpty()) {
            return ConflictKind.NONE;
        }
        if (isBlank(currentTurnId)) {
            return ConflictKind.UNRESOLVED_NO_WINNER;
        }
        List<IntentConstraint> currentConstraints = constraints.stream()
                .filter(item -> currentTurnId.equals(item.originTurnId()))
                .toList();
        if (currentConstraints.isEmpty()) {
            return ConflictKind.UNRESOLVED_NO_WINNER;
        }
        if (intersection(currentConstraints).isEmpty()) {
            return ConflictKind.UNRESOLVED_CURRENT_TURN;
        }
        return ConflictKind.CROSS_TURN;
    }

    private Set<String> intersection(List<IntentConstraint> constraints) {
        Set<String> common = new LinkedHashSet<>(constraints.getFirst().values());
        constraints.stream().skip(1).forEach(item -> common.retainAll(item.values()));
        return common;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private enum ConflictKind {
        NONE,
        CROSS_TURN,
        UNRESOLVED_CURRENT_TURN,
        UNRESOLVED_NO_WINNER
    }
}
