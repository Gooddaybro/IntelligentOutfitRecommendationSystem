package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantRecommendationItem;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.MatchedDimension;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.RecommendationDecision;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.RecommendationStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Owns the final recommendation state and validates every Python claim against Java candidate facts.
 */
public class RecommendationDecisionService {

    private final OutfitRoleValidator roleValidator = new OutfitRoleValidator();

    /**
     * 从同一份不可变 Java 候选池生成最终决策。
     *
     * 无候选和零采信引用分别降级为 EMPTY 与 BROWSE_FALLBACK；搭配建议只有同时覆盖上装和下装才是完整匹配。
     * 决策异常不在此处吞掉，由调用方统一映射为 FAILED，防止正常降级与系统故障混淆。
     */
    public RecommendationDecision decide(
            DemandIntent intent,
            List<RecommendationCandidate> candidates,
            List<PythonProductRef> refs
    ) {
        List<RecommendationCandidate> safeCandidates = candidates == null ? List.of() : candidates;
        List<PythonProductRef> safeRefs = refs == null ? List.of() : refs;
        if (safeCandidates.isEmpty()) {
            return new RecommendationDecision(RecommendationStatus.EMPTY, List.of(), safeRefs.size());
        }

        Map<String, RecommendationCandidate> candidateById = new LinkedHashMap<>();
        for (RecommendationCandidate candidate : safeCandidates) {
            if (candidate != null && candidate.getSpuId() != null && candidate.getSkuId() != null) {
                candidateById.put(key(candidate.getSpuId(), candidate.getSkuId()), candidate);
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        List<AssistantRecommendationItem> accepted = new ArrayList<>();
        int discarded = 0;
        for (PythonProductRef ref : safeRefs) {
            RecommendationCandidate candidate = ref == null ? null
                    : candidateById.get(key(ref.spuId(), ref.skuId()));
            if (candidate == null || !seen.add(key(ref.spuId(), ref.skuId()))
                    || !isPurchasable(candidate) || !hasValidEvidence(intent, candidate, ref.matchedDimensions())) {
                discarded++;
                continue;
            }
            accepted.add(new AssistantRecommendationItem(
                    ref.spuId(), ref.skuId(), normalizeReason(ref.reason()), ref.rankScore(),
                    ref.matchedDimensions(), roleValidator.validate(candidate.getCategoryName(), ref.outfitRole())
            ));
        }
        return new RecommendationDecision(
                resolveStatus(intent, accepted),
                accepted,
                discarded
        );
    }

    private RecommendationStatus resolveStatus(DemandIntent intent, List<AssistantRecommendationItem> accepted) {
        if (accepted.isEmpty()) {
            return RecommendationStatus.BROWSE_FALLBACK;
        }
        if (intent == null || !"OUTFIT_ADVICE".equals(intent.requestType())) {
            return RecommendationStatus.STRONG_MATCH;
        }
        Set<String> acceptedRoles = accepted.stream()
                .map(AssistantRecommendationItem::outfitRole)
                .collect(java.util.stream.Collectors.toSet());
        return acceptedRoles.contains("TOP") && acceptedRoles.contains("BOTTOM")
                ? RecommendationStatus.STRONG_MATCH
                : RecommendationStatus.PARTIAL_MATCH;
    }

    private boolean hasValidEvidence(
            DemandIntent intent,
            RecommendationCandidate candidate,
            List<MatchedDimension> evidence
    ) {
        return intent != null && evidence != null && !evidence.isEmpty()
                && evidence.stream().allMatch(item -> evidenceMatches(intent, candidate, item));
    }

    private boolean evidenceMatches(DemandIntent intent, RecommendationCandidate candidate, MatchedDimension item) {
        if (item == null || !hasText(item.dimension()) || !hasText(item.requestedValue())
                || !hasText(item.candidateValue()) || !hasText(item.evidenceSource())) {
            return false;
        }
        return switch (item.dimension()) {
            case "category" -> "PRODUCT_CATEGORY".equals(item.evidenceSource())
                    && equalsValue(intent.category(), item.requestedValue())
                    && equalsValue(candidate.getCategoryName(), item.candidateValue());
            case "season" -> "PRODUCT_SEASON".equals(item.evidenceSource())
                    && equalsValue(intent.season(), item.requestedValue())
                    && containsCsv(candidate.getSeasons(), item.candidateValue());
            case "style" -> "PRODUCT_STYLE_TAG".equals(item.evidenceSource())
                    && containsValue(intent.style(), item.requestedValue())
                    && (containsCsv(candidate.getStyleTags(), item.candidateValue())
                    || containsTagValue(candidate.getAttributeTags(), item.candidateValue()));
            case "fit", "fitPreferences" -> "PRODUCT_FIT".equals(item.evidenceSource())
                    && containsValue(intent.fitPreferences(), item.requestedValue())
                    && equalsValue(candidate.getFitType(), item.candidateValue());
            case "attribute", "attributes" -> "PRODUCT_ATTRIBUTE".equals(item.evidenceSource())
                    && containsValue(intent.attributes(), item.requestedValue())
                    && containsTagValue(candidate.getAttributeTags(), item.candidateValue());
            case "budgetMax" -> budgetMatches(intent, candidate, item);
            default -> false;
        };
    }

    private boolean budgetMatches(DemandIntent intent, RecommendationCandidate candidate, MatchedDimension item) {
        if (!"PRODUCT_PRICE".equals(item.evidenceSource()) || intent.budgetMax() == null
                || candidate.getSalePrice() == null) {
            return false;
        }
        try {
            BigDecimal requested = new BigDecimal(item.requestedValue());
            BigDecimal candidateValue = new BigDecimal(item.candidateValue());
            return requested.compareTo(BigDecimal.valueOf(intent.budgetMax())) == 0
                    && candidateValue.compareTo(candidate.getSalePrice()) == 0
                    && candidate.getSalePrice().compareTo(requested) <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isPurchasable(RecommendationCandidate candidate) {
        if (candidate.getAvailableStock() != null && candidate.getAvailableStock() <= 0) {
            return false;
        }
        String status = normalize(candidate.getStockStatus());
        return !status.contains("out_of_stock") && !status.contains("sold_out")
                && !status.contains("off_sale") && !status.contains("下架") && !status.contains("售罄");
    }

    private boolean containsCsv(String csv, String expected) {
        if (!hasText(csv)) {
            return false;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(value -> equalsValue(value, expected));
    }

    private boolean containsTagValue(String csv, String expected) {
        if (!hasText(csv)) {
            return false;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(value -> value.contains(":") ? value.substring(value.indexOf(':') + 1) : value)
                .anyMatch(value -> equalsValue(value, expected));
    }

    private boolean containsValue(List<String> values, String expected) {
        return values != null && values.stream().anyMatch(value -> equalsValue(value, expected));
    }

    private boolean equalsValue(String first, String second) {
        return hasText(first) && hasText(second) && normalize(first).equals(normalize(second));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String key(Long spuId, Long skuId) {
        return spuId + ":" + skuId;
    }

    private String normalizeReason(String reason) {
        return hasText(reason) ? reason.trim() : "符合本轮明确需求。";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
