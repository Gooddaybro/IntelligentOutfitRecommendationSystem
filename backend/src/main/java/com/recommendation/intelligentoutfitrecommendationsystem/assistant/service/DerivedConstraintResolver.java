package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintStrength;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the lifecycle of system-derived preferences linked to active parent constraints.
 * User-explicit preferences are never invalidated here, even when their combination is uncommon.
 */
public class DerivedConstraintResolver {

    private static final Map<String, List<DerivedValue>> SEASON_DERIVATIONS = Map.of(
            "WINTER", List.of(new DerivedValue("thermal", "WARM"), new DerivedValue("thickness", "THICK")),
            "SUMMER", List.of(
                    new DerivedValue("materialFeature", "BREATHABLE"),
                    new DerivedValue("thickness", "LIGHTWEIGHT"),
                    new DerivedValue("thermal", "COOLING"))
    );

    /**
     * Removes orphaned, unsupported, or stale system derivations and then rebuilds the active season's preferences.
     * Semantic deduplication makes repeated resolution idempotent and favors existing explicit values. Because this
     * contract models only positive constraints, an active season always rebuilds its positive derivations; persistent
     * negative exclusions require a future, separate constraint contract.
     *
     * @param demand merged effective demand
     * @return a snapshot with current parent-linked derived preferences
     */
    public EffectiveDemand resolve(EffectiveDemand demand) {
        if (demand == null) {
            throw new IllegalArgumentException("effective demand is required");
        }
        List<IntentConstraint> surviving = demand.softPreferences().stream()
                .filter(item -> !item.isDerived() || derivedConstraintStillValid(item, demand.hardFilters()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        demand.hardFilters().stream()
                .filter(item -> item.field().equals("season"))
                .forEach(parent -> parent.values().forEach(value ->
                        SEASON_DERIVATIONS.getOrDefault(value, List.of())
                                .forEach(derived -> surviving.add(toConstraint(parent, derived)))));
        return demand.withSoftPreferences(deduplicate(surviving));
    }

    private boolean derivedConstraintStillValid(
            IntentConstraint derived,
            List<IntentConstraint> activeHardConstraints
    ) {
        Optional<IntentConstraint> parent = activeHardConstraints.stream()
                .filter(item -> item.id().equals(derived.derivedFromConstraintId()))
                .findFirst();
        if (parent.isEmpty()) {
            return false;
        }
        if (!parent.get().field().equals("season")) {
            return false;
        }
        return parent.get().values().stream()
                .flatMap(value -> SEASON_DERIVATIONS.getOrDefault(value, List.of()).stream())
                .anyMatch(expected -> expected.field().equals(derived.field())
                        && derived.values().equals(List.of(expected.value())));
    }

    private IntentConstraint toConstraint(IntentConstraint parent, DerivedValue derived) {
        String id = parent.id() + "-derived-" + derived.field() + "-" + derived.value().toLowerCase();
        return new IntentConstraint(
                id,
                derived.field(),
                ConstraintOperator.CONTAINS,
                List.of(derived.value()),
                ConstraintStrength.SOFT,
                ConstraintOrigin.SYSTEM_DERIVED,
                parent.originTurnId(),
                parent.id(),
                parent.scope(),
                null
        );
    }

    private List<IntentConstraint> deduplicate(List<IntentConstraint> constraints) {
        Map<String, IntentConstraint> unique = new LinkedHashMap<>();
        constraints.forEach(item -> unique.merge(semanticKey(item), item, this::preferBySource));
        return List.copyOf(unique.values());
    }

    private IntentConstraint preferBySource(IntentConstraint existing, IntentConstraint candidate) {
        return IntentConstraintMerger.sourceRank(candidate.origin())
                > IntentConstraintMerger.sourceRank(existing.origin()) ? candidate : existing;
    }

    private String semanticKey(IntentConstraint constraint) {
        return constraint.field() + "\u0000" + constraint.operator() + "\u0000" + constraint.values();
    }

    private record DerivedValue(String field, String value) {
    }
}
