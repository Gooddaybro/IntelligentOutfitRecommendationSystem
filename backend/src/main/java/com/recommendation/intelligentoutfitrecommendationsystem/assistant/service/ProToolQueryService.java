package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProToolResult;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryQueryService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductFactsQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whitelisted read-only Pro tools; delegates facts to existing catalog/inventory services.
 * Raw arguments cannot select identity, transport addresses, database operations or extra filters.
 */
@Service
public class ProToolQueryService {
    private final ProRunRegistry registry;
    private final RecommendationCandidateQueryService candidates;
    private final ProductFactsQuery catalog;
    private final InventoryQueryService inventory;

    public ProToolQueryService(ProRunRegistry registry, RecommendationCandidateQueryService candidates,
                               ProductFactsQuery catalog, InventoryQueryService inventory) {
        this.registry = registry;
        this.candidates = candidates;
        this.catalog = catalog;
        this.inventory = inventory;
    }

    /**
     * Authenticates before data access and registers only facts included in successful responses.
     * Bad arguments remain HTTP errors; missing catalog entries are empty, dependency errors unavailable.
     */
    public ProToolResult execute(String runId, String token, String tool, Map<String, Object> args) {
        registry.authorize(runId, token);
        Set<String> allowed = switch (tool) {
            case "search_products" -> Set.of("category", "style", "season", "material", "fit", "gender", "recall_text", "budget_max");
            case "get_product_detail" -> Set.of("spu_id");
            case "check_availability" -> Set.of("spu_id", "color", "size");
            default -> throw new BadRequestException("Unknown Pro tool");
        };
        if (args == null || !allowed.containsAll(args.keySet())) {
            throw new BadRequestException("Unknown tool arguments");
        }
        // Validate all values before entering the dependency-failure boundary.
        Long spuId = tool.equals("search_products") ? null : positiveId(args.get("spu_id"));
        for (String field : allowed) {
            if (!field.equals("spu_id") && args.containsKey(field)) {
                text(args, field, false);
            }
        }
        BigDecimal budget = budget(args);
        if (tool.equals("check_availability")) {
            text(args, "color", true);
            text(args, "size", true);
        }
        try {
            return switch (tool) {
                case "search_products" -> search(runId, token, args, budget);
                case "get_product_detail" -> detail(runId, token, spuId);
                case "check_availability" -> availability(runId, token, spuId, args);
                default -> throw new IllegalStateException("Validated tool expected");
            };
        } catch (ProRunRegistry.RegistryException | BadRequestException error) {
            throw error;
        } catch (RuntimeException error) {
            return ProToolResult.of("unavailable", null, "dependency_unavailable");
        }
    }

    /** Recall rounds up only for the legacy integer DTO; returned SKU prices use the original decimal ceiling. */
    private ProToolResult search(String runId, String token, Map<String, Object> args, BigDecimal budget) {
        var query = new RecommendationCandidateQuery(text(args, "category", false), text(args, "style", false),
                text(args, "season", false), text(args, "material", false), text(args, "fit", false),
                budget == null ? null : budget.setScale(0, RoundingMode.CEILING).intValueExact(),
                text(args, "gender", false), text(args, "recall_text", false));
        List<RecommendationCandidate> returned = candidates.findCandidates(query).stream()
                .filter(candidate -> candidate.getSalePrice() != null
                        && (budget == null || candidate.getSalePrice().compareTo(budget) <= 0))
                .limit(20).toList();
        if (returned.isEmpty()) {
            registry.authorize(runId, token);
            return ProToolResult.of("empty", List.of(), null);
        }
        List<Map<String, Object>> data = returned.stream().map(this::candidateData).toList();
        registry.registerCandidates(runId, token, returned);
        return ProToolResult.of("ok", data, null);
    }

    private ProToolResult detail(String runId, String token, Long spuId) {
        com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail detail;
        try {
            detail = catalog.getProductDetail(spuId);
        } catch (ResourceNotFoundException error) {
            registry.authorize(runId, token);
            return ProToolResult.of("empty", null, null);
        }
        if (!spuId.equals(detail.getSpuId())) {
            return ProToolResult.of("unavailable", null, "dependency_unavailable");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("spu_id", detail.getSpuId());
        data.put("name", detail.getName());
        data.put("description", detail.getDescription());
        data.put("main_image_url", detail.getMainImageUrl());
        data.put("min_price", decimal(detail.getMinPrice()));
        data.put("max_price", decimal(detail.getMaxPrice()));
        data.put("attributes", detail.getAttributes() == null ? Map.of() : detail.getAttributes());
        data.put("style_tags", detail.getStyleTags());
        data.put("materials", detail.getMaterials());
        data.put("fit_type", detail.getFitType());
        data.put("category", detail.getCategoryName());
        registry.registerDetail(runId, token, detail.getSpuId());
        return ProToolResult.of("ok", data, null);
    }

    /** A missing SKU is empty; missing inventory for a resolved SKU cannot be reported as zero stock. */
    private ProToolResult availability(String runId, String token, Long spuId, Map<String, Object> args) {
        com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem sku;
        try {
            sku = catalog.findSku(spuId, text(args, "color", true), text(args, "size", true));
        } catch (ResourceNotFoundException error) {
            registry.authorize(runId, token);
            return ProToolResult.of("empty", null, null);
        }
        var stock = inventory.getInventoryBySkuId(sku.getSkuId());
        if (!spuId.equals(sku.getSpuId()) || !sku.getSkuId().equals(stock.getSkuId())
                || !spuId.equals(stock.getSpuId()) || stock.getAvailableStock() == null || stock.getAvailableStock() < 0) {
            return ProToolResult.of("unavailable", null, "dependency_unavailable");
        }
        var candidate = new RecommendationCandidate();
        candidate.setSpuId(sku.getSpuId());
        candidate.setSkuId(sku.getSkuId());
        candidate.setSalePrice(sku.getSalePrice());
        candidate.setColor(sku.getColor());
        candidate.setSize(sku.getSize());
        candidate.setAvailableStock(stock.getAvailableStock());
        Map<String, Object> data = skuData(candidate);
        registry.registerCandidates(runId, token, List.of(candidate));
        return ProToolResult.of("ok", data, null);
    }

    private Map<String, Object> candidateData(RecommendationCandidate candidate) {
        Map<String, Object> data = skuData(candidate);
        data.put("name", candidate.getName());
        data.put("main_image_url", candidate.getMainImageUrl());
        data.put("category", candidate.getCategoryName());
        data.put("material", candidate.getMaterials());
        data.put("fit_type", candidate.getFitType());
        data.put("style_tags", candidate.getStyleTags());
        data.put("attribute_tags", candidate.getAttributeTags());
        return data;
    }

    private Map<String, Object> skuData(RecommendationCandidate candidate) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("spu_id", candidate.getSpuId());
        data.put("sku_id", candidate.getSkuId());
        data.put("color", candidate.getColor());
        data.put("size", candidate.getSize());
        data.put("sale_price", decimal(candidate.getSalePrice()));
        data.put("available_stock", candidate.getAvailableStock());
        return data;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String text(Map<String, Object> args, String name, boolean required) {
        Object value = args.get(name);
        if (!args.containsKey(name) && !required) { return null; }
        if (!(value instanceof String string) || string.isBlank() || string.length() > 2000) {
            throw new BadRequestException("Invalid " + name);
        }
        return ((String) value).trim();
    }

    private static Long positiveId(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger)) {
            throw new BadRequestException("spu_id must be a positive integer");
        }
        try {
            long id = new BigInteger(value.toString()).longValueExact();
            if (id > 0) { return id; }
        } catch (ArithmeticException error) {
            throw new BadRequestException("spu_id out of range");
        }
        throw new BadRequestException("spu_id must be positive");
    }

    private static BigDecimal budget(Map<String, Object> args) {
        if (!args.containsKey("budget_max")) { return null; }
        String value = text(args, "budget_max", false);
        if (!value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?")) {
            throw new BadRequestException("budget_max must be a decimal CNY string");
        }
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new BadRequestException("budget_max out of range");
        }
        return decimal;
    }
}
