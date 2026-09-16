package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Converts untrusted Pro references into cards using the active Java ledger and fresh catalog facts.
 * Python prose and requirement evidence IDs cannot establish commerce or policy truth.
 */
@Service
public class ProRecommendationValidator {
    private static final Set<String> STOP_REASONS = Set.of("completed", "needs_input", "budget_exhausted",
            "validation_failed", "dependency_unavailable", "invalid_response", "cancelled");
    private final ProRunRegistry registry;
    private final RecommendationCandidateQueryService catalog;

    public ProRecommendationValidator(ProRunRegistry registry, RecommendationCandidateQueryService catalog) {
        this.registry = registry;
        this.catalog = catalog;
    }

    /**
     * Validates while credentials remain active; identity failures abort without catalog reads.
     * Fresh SQL applies explicit catalog filters, then the exact decimal budget is checked here.
     * Until evidence has a Java-verifiable authority, Python satisfied claims remain unconfirmed.
     */
    public JsonNode validate(Long userId, String threadId, String requestId, ProRunRegistry.RunCredentials run,
                             ProChatRequest filters, JsonNode done) {
        var snapshot = registry.snapshot(run.runId(), run.token());
        if (!Objects.equals(userId, snapshot.userId()) || !Objects.equals(threadId, snapshot.threadId())
                || !Objects.equals(requestId, snapshot.requestId()) || done == null
                || !threadId.equals(done.path("thread_id").asText()) || !requestId.equals(done.path("request_id").asText())
                || !run.runId().equals(done.path("run_id").asText())
                || !"pro".equals(done.path("agent_mode").asText())
                || !"assistant-v2".equals(done.path("contract_version").asText())
                || !done.path("product_refs").isArray() || done.path("product_refs").size() > 20
                || !done.path("requirements").isArray() || done.path("requirements").size() > 12) {
            throw new ProRunRegistry.RegistryException(ProRunRegistry.Failure.FORBIDDEN);
        }
        String stopReason = done.path("stop_reason").asText("");
        if (stopReason.isBlank()) {
            stopReason = "completed";
        }
        if (!STOP_REASONS.contains(stopReason)) {
            throw new ProRunRegistry.RegistryException(ProRunRegistry.Failure.FORBIDDEN);
        }
        boolean completed = "completed".equals(stopReason);
        boolean failed = !completed && !"needs_input".equals(stopReason);
        var eligible = new LinkedHashMap<String, JsonNode>();
        if (completed) {
            for (JsonNode ref : done.path("product_refs")) {
                if (!ref.path("spu_id").isIntegralNumber() || !ref.path("sku_id").isIntegralNumber()
                        || !ref.path("spu_id").canConvertToLong() || !ref.path("sku_id").canConvertToLong()) { continue; }
                String key = ref.path("spu_id").longValue() + ":" + ref.path("sku_id").longValue();
                var registered = snapshot.candidates().get(key);
                if (registered != null && registered.spuId().equals(ref.path("spu_id").longValue())
                        && registered.skuId().equals(ref.path("sku_id").longValue())) { eligible.putIfAbsent(key, ref); }
            }
        }
        List<Long> spuIds = eligible.values().stream().map(ref -> ref.path("spu_id").longValue()).distinct().toList();
        var query = new RecommendationCandidateQuery(filters.category(), filters.style(), filters.season(),
                filters.material(), filters.fit(), null, filters.gender(), null);
        List<RecommendationCandidate> live = completed && !spuIds.isEmpty()
                ? catalog.findFreshCandidates(query, spuIds) : List.of();
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("thread_id", threadId).put("run_id", run.runId()).put("agent_mode", "pro");
        var items = result.putArray("recommended_items");
        var ids = result.putArray("recommended_spu_ids");
        var seen = new LinkedHashSet<String>();
        var seenSpus = new LinkedHashSet<Long>();
        for (var c : live) {
            String key = c.getSpuId() + ":" + c.getSkuId();
            if (!eligible.containsKey(key) || !seen.add(key) || c.getSalePrice() == null
                    || c.getSalePrice().signum() < 0 || c.getAvailableStock() == null || c.getAvailableStock() <= 0
                    || (filters.budgetMax() != null && c.getSalePrice().compareTo(filters.budgetMax()) > 0)) { continue; }
            ObjectNode card = items.addObject();
            card.put("spu_id", c.getSpuId()).put("sku_id", c.getSkuId()).put("name", c.getName())
                    .put("sale_price", c.getSalePrice().toPlainString()).put("main_image_url", c.getMainImageUrl())
                    .put("color", c.getColor()).put("size", c.getSize()).put("available_stock", c.getAvailableStock())
                    .put("reason", "商品已通过本轮商品来源、明确筛选条件及当前价格库存校验。");
            if ("generic_rule".equals(eligible.get(key).path("basis").asText())) {
                card.put("basis", "generic_rule").put("size_advice", "通用尺码规则仅供参考，不保证合身；请核对商品尺码表。");
            }
            if (seenSpus.add(c.getSpuId())) { ids.add(c.getSpuId()); }
        }
        var requirements = result.putArray("requirements");
        for (JsonNode requirement : done.path("requirements")) {
            var safe = requirements.addObject();
            safe.put("id", bounded(requirement.path("id").asText(), 100));
            safe.put("text", bounded(requirement.path("text").asText(), 500));
            safe.put("status", "needs_input".equals(requirement.path("status").asText()) ? "needs_input" : "unconfirmed");
            safe.putArray("evidence_ids");
        }
        if (requirements.isEmpty()) {
            var queryRequirement = requirements.addObject();
            queryRequirement.put("id", "query");
            queryRequirement.put("text", bounded(filters.message(), 500));
            queryRequirement.put("status", "unconfirmed");
            queryRequirement.putArray("evidence_ids");
        }
        boolean partial = !requirements.isEmpty() || items.size() != done.path("product_refs").size();
        result.put("recommendation_status", failed ? "FAILED" : items.isEmpty() ? "EMPTY" : partial ? "PARTIAL_MATCH" : "STRONG_MATCH");
        result.put("answer", items.isEmpty() ? "未能确认符合本轮条件且当前有库存的商品；其他需求仍待确认。"
                : "已核验以下 " + items.size() + " 件商品的当前价格、库存与明确筛选条件。"
                    + (partial ? "部分需求未能确认；尺码适配及退换政策请进一步核实。" : "请以商品详情和下单时信息为准。"));
        registry.authorize(run.runId(), run.token());
        return result;
    }

    private String bounded(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
