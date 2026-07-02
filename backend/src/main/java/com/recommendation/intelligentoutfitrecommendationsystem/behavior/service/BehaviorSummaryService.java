package com.recommendation.intelligentoutfitrecommendationsystem.behavior.service;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.BehaviorMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorProductSignal;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将近期行为流水聚合成 AI 可消费的轻量偏好摘要。
 */
@Service
public class BehaviorSummaryService {

    private static final int WINDOW_DAYS = 30;
    private static final int SIGNAL_LIMIT = 200;
    private static final int SPU_ID_LIMIT = 10;
    private static final int PREFERENCE_LIMIT = 5;
    private static final Set<String> INTEREST_EVENTS = Set.of(
            "RECOMMENDATION_CLICKED",
            "RECOMMENDATION_FAVORITE_ADD",
            "FAVORITE_ADD"
    );
    private static final Set<String> CART_EVENTS = Set.of("RECOMMENDATION_CART_ADD", "CART_ADD");
    private static final Set<String> PURCHASE_EVENTS = Set.of("PAYMENT_SUCCESS");
    private static final Set<String> EXPOSED_EVENTS = Set.of("RECOMMENDATION_EXPOSED");

    private final BehaviorMapper behaviorMapper;

    public BehaviorSummaryService(BehaviorMapper behaviorMapper) {
        this.behaviorMapper = behaviorMapper;
    }

    public BehaviorSummaryResponse getSummary(Long userId) {
        validateUserId(userId);
        List<BehaviorProductSignal> signals = behaviorMapper.findRecentSignals(
                userId,
                LocalDateTime.now().minusDays(WINDOW_DAYS),
                SIGNAL_LIMIT
        );
        List<BehaviorProductSignal> safeSignals = signals == null ? List.of() : signals;
        return new BehaviorSummaryResponse(
                collectSpuIds(safeSignals, INTEREST_EVENTS),
                collectSpuIds(safeSignals, CART_EVENTS),
                collectSpuIds(safeSignals, PURCHASE_EVENTS),
                collectCategories(safeSignals),
                collectStyles(safeSignals),
                collectSpuIds(safeSignals, EXPOSED_EVENTS)
        );
    }

    private List<Long> collectSpuIds(List<BehaviorProductSignal> signals, Set<String> eventTypes) {
        LinkedHashSet<Long> spuIds = new LinkedHashSet<>();
        for (BehaviorProductSignal signal : signals) {
            if (spuIds.size() >= SPU_ID_LIMIT) {
                break;
            }
            if (signal == null || signal.getSpuId() == null || !eventTypes.contains(normalizeEventType(signal))) {
                continue;
            }
            spuIds.add(signal.getSpuId());
        }
        return List.copyOf(spuIds);
    }

    private List<String> collectCategories(List<BehaviorProductSignal> signals) {
        PreferenceAccumulator accumulator = new PreferenceAccumulator();
        int index = 0;
        for (BehaviorProductSignal signal : signals) {
            if (isPreferenceSignal(signal) && StringUtils.hasText(signal.getCategoryName())) {
                accumulator.add(signal.getCategoryName().trim(), index);
            }
            index++;
        }
        return accumulator.topValues();
    }

    private List<String> collectStyles(List<BehaviorProductSignal> signals) {
        PreferenceAccumulator accumulator = new PreferenceAccumulator();
        int index = 0;
        for (BehaviorProductSignal signal : signals) {
            if (isPreferenceSignal(signal)) {
                for (String style : csvValues(signal.getStyleTags())) {
                    accumulator.add(style, index);
                }
            }
            index++;
        }
        return accumulator.topValues();
    }

    private boolean isPreferenceSignal(BehaviorProductSignal signal) {
        if (signal == null) {
            return false;
        }
        String eventType = normalizeEventType(signal);
        return INTEREST_EVENTS.contains(eventType) || CART_EVENTS.contains(eventType) || PURCHASE_EVENTS.contains(eventType);
    }

    private List<String> csvValues(String rawValues) {
        if (!StringUtils.hasText(rawValues)) {
            return List.of();
        }
        LinkedHashSet<String> values = Arrays.stream(rawValues.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        return List.copyOf(values);
    }

    private String normalizeEventType(BehaviorProductSignal signal) {
        String eventType = signal.getEventType();
        return eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }

    private static final class PreferenceAccumulator {
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final Map<String, Integer> firstIndexes = new LinkedHashMap<>();

        private void add(String value, int index) {
            counts.merge(value, 1, Integer::sum);
            firstIndexes.putIfAbsent(value, index);
        }

        private List<String> topValues() {
            return counts.keySet().stream()
                    .sorted(Comparator
                            .comparing((String value) -> counts.get(value)).reversed()
                            .thenComparing(firstIndexes::get))
                    .limit(PREFERENCE_LIMIT)
                    .toList();
        }
    }
}
