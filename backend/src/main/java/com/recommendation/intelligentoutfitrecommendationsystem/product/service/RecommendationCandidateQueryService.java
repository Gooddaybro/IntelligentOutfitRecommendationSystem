package com.recommendation.intelligentoutfitrecommendationsystem.product.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheKeyConstants;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateLiveFact;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ElasticsearchProductSearchGateway;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchCriteria;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchGateway;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchUnavailableException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.RecommendationEsRecallProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 推荐候选查询边界，隐藏静态快照缓存、实时事实补齐和可售过滤细节。
 *
 * <p>返回给 Assistant/Python 的价格和库存每次都来自 MySQL 实时事实；Redis 只缓存不含
 * 交易事实的静态候选快照。</p>
 */
@Service
public class RecommendationCandidateQueryService {

    private final ProductMapper productMapper;
    private final RedisCacheService redisCacheService;
    private final CacheTtlProperties cacheTtlProperties;
    private final ProductSearchGateway recommendationSearchGateway;
    private final RecommendationEsRecallProperties recallProperties;
    private final ApplicationMetrics metrics;

    @Autowired
    public RecommendationCandidateQueryService(
            ProductMapper productMapper,
            RedisCacheService redisCacheService,
            CacheTtlProperties cacheTtlProperties,
            ObjectProvider<ElasticsearchProductSearchGateway> recommendationSearchGateway,
            RecommendationEsRecallProperties recallProperties,
            ApplicationMetrics metrics
    ) {
        this(
                productMapper,
                redisCacheService,
                cacheTtlProperties,
                recommendationSearchGateway.getIfAvailable(),
                recallProperties,
                metrics
        );
    }

    public RecommendationCandidateQueryService(
            ProductMapper productMapper,
            RedisCacheService redisCacheService,
            CacheTtlProperties cacheTtlProperties
    ) {
        this(productMapper, redisCacheService, cacheTtlProperties,
                (ProductSearchGateway) null, new RecommendationEsRecallProperties(), null);
    }

    public RecommendationCandidateQueryService(
            ProductMapper productMapper,
            RedisCacheService redisCacheService,
            CacheTtlProperties cacheTtlProperties,
            ProductSearchGateway recommendationSearchGateway,
            RecommendationEsRecallProperties recallProperties,
            ApplicationMetrics metrics
    ) {
        this.productMapper = productMapper;
        this.redisCacheService = redisCacheService;
        this.cacheTtlProperties = cacheTtlProperties;
        this.recommendationSearchGateway = recommendationSearchGateway;
        this.recallProperties = recallProperties == null ? new RecommendationEsRecallProperties() : recallProperties;
        this.metrics = metrics;
    }

    /**
     * 根据 Java 解析后的硬过滤条件返回当前可售推荐候选。
     *
     * @param query 类目、风格、季节、材质、版型、预算和性别过滤条件
     * @return 已补齐实时价格和库存并按确定性规则排序的候选
     */
    public List<RecommendationCandidate> findCandidates(RecommendationCandidateQuery query) {
        long startedNanos = System.nanoTime();
        RecommendationCandidateQuery normalizedQuery = normalizeRecommendationQuery(query);
        if (normalizedQuery.getBudgetMax() != null && normalizedQuery.getBudgetMax() < 0) {
            throw new BadRequestException("budgetMax must not be negative");
        }
        boolean useEsRecall = shouldUseEsRecall(normalizedQuery);
        String cacheKey = recommendationCandidatesCacheKey(normalizedQuery, useEsRecall);
        var cachedSnapshots = redisCacheService.getList(cacheKey, RecommendationCandidateSnapshot.class);
        SnapshotLookup lookup;
        if (cachedSnapshots.isPresent()) {
            lookup = new SnapshotLookup(
                    cachedSnapshots.get(),
                    useEsRecall,
                    useEsRecall ? "elasticsearch" : "mysql",
                    useEsRecall ? "success" : "disabled",
                    null,
                    false);
        } else {
            lookup = useEsRecall
                    ? findEsRecallSnapshotLookup(normalizedQuery)
                    : findMySqlSnapshots(normalizedQuery, "disabled", true);
            if (lookup.cacheable()) {
                redisCacheService.setValue(
                        cacheKey,
                        lookup.snapshots(),
                        cacheTtlProperties.recommendationCandidatesTtl()
                );
            }
        }
        List<RecommendationCandidate> candidates = hydrateRecommendationCandidates(
                lookup.snapshots(), normalizedQuery.getBudgetMax(), lookup.preserveSnapshotOrder());
        recordRecallMetrics(lookup, candidates.size(), elapsed(startedNanos));
        return candidates;
    }

    /**
     * 为商城内已确定的 SPU 补齐当前可展示和可购买的商品事实。
     *
     * 收藏模块只持久化用户与 SPU 的关系；这里复用候选商品的快照和实时价格、库存补齐规则，
     * 避免收藏列表返回关系表字段而让前端无法展示商品。
     *
     * @param spuIds 按调用方业务顺序排列的 SPU 标识
     * @return 每个仍可购买 SPU 的一个商品候选，保持传入 SPU 顺序
     */
    public List<RecommendationCandidate> findCandidatesBySpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return List.of();
        }
        List<RecommendationCandidateSnapshot> snapshots = orderSnapshotsBySpuIds(
                productMapper.findRecommendationCandidateSnapshotsBySpuIds(
                        new RecommendationCandidateQuery(), spuIds),
                spuIds);
        Map<Long, RecommendationCandidate> candidatesBySpuId = new LinkedHashMap<>();
        for (RecommendationCandidate candidate : hydrateRecommendationCandidates(snapshots, null, true)) {
            candidatesBySpuId.putIfAbsent(candidate.getSpuId(), candidate);
        }
        return List.copyOf(candidatesBySpuId.values());
    }

    private boolean shouldUseEsRecall(RecommendationCandidateQuery query) {
        return recallProperties.isEnabled()
                && recommendationSearchGateway != null
                && hasText(query.getRecallText());
    }

    private SnapshotLookup findEsRecallSnapshotLookup(RecommendationCandidateQuery query) {
        List<Long> orderedSpuIds;
        try {
            orderedSpuIds = recommendationSearchGateway.search(
                    new ProductSearchCriteria(query.getRecallText(), query.getCategory(), recallProperties.getLimit()));
        } catch (ProductSearchUnavailableException exception) {
            recordRecall("elasticsearch", "unavailable", Duration.ZERO);
            return findMySqlSnapshots(query, "fallback", false);
        } catch (RuntimeException exception) {
            recordRecall("elasticsearch", "error", Duration.ZERO);
            throw exception;
        }
        if (orderedSpuIds.isEmpty()) {
            return new SnapshotLookup(List.of(), true, "elasticsearch", "empty", 0, true);
        }
        List<RecommendationCandidateSnapshot> snapshots =
                productMapper.findRecommendationCandidateSnapshotsBySpuIds(query, orderedSpuIds);
        return new SnapshotLookup(
                orderSnapshotsBySpuIds(snapshots, orderedSpuIds),
                true,
                "elasticsearch",
                "success",
                orderedSpuIds.size(),
                true);
    }

    private SnapshotLookup findMySqlSnapshots(
            RecommendationCandidateQuery query,
            String outcome,
            boolean cacheable
    ) {
        return new SnapshotLookup(
                productMapper.findRecommendationCandidateSnapshots(query),
                false,
                "mysql",
                outcome,
                null,
                cacheable);
    }

    private List<RecommendationCandidateSnapshot> orderSnapshotsBySpuIds(
            List<RecommendationCandidateSnapshot> snapshots,
            List<Long> orderedSpuIds
    ) {
        Map<Long, Integer> spuOrder = new HashMap<>();
        for (int index = 0; index < orderedSpuIds.size(); index++) {
            spuOrder.putIfAbsent(orderedSpuIds.get(index), index);
        }
        return snapshots.stream()
                .sorted(Comparator
                        .comparing((RecommendationCandidateSnapshot snapshot) ->
                                spuOrder.getOrDefault(snapshot.getSpuId(), Integer.MAX_VALUE))
                        .thenComparing(RecommendationCandidateSnapshot::getSkuId))
                .toList();
    }

    private List<RecommendationCandidate> hydrateRecommendationCandidates(
            List<RecommendationCandidateSnapshot> snapshots,
            Integer budgetMax,
            boolean preserveSnapshotOrder
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<Long> skuIds = snapshots.stream()
                .map(RecommendationCandidateSnapshot::getSkuId)
                .distinct()
                .toList();
        Map<Long, RecommendationCandidateLiveFact> factsBySkuId = productMapper
                .findRecommendationCandidateLiveFacts(skuIds)
                .stream()
                .collect(Collectors.toMap(
                        RecommendationCandidateLiveFact::getSkuId,
                        Function.identity()
                ));
        Map<Long, Integer> skuOrder = new HashMap<>();
        for (int index = 0; index < snapshots.size(); index++) {
            skuOrder.putIfAbsent(snapshots.get(index).getSkuId(), index);
        }
        Comparator<RecommendationCandidate> comparator = preserveSnapshotOrder
                ? Comparator
                .comparing((RecommendationCandidate candidate) ->
                        skuOrder.getOrDefault(candidate.getSkuId(), Integer.MAX_VALUE))
                .thenComparing(RecommendationCandidate::getSkuId)
                : Comparator
                .comparing(RecommendationCandidate::getAvailableStock, Comparator.reverseOrder())
                .thenComparing(RecommendationCandidate::getSalePrice)
                .thenComparing(RecommendationCandidate::getSpuId)
                .thenComparing(RecommendationCandidate::getSkuId);
        return snapshots.stream()
                .filter(snapshot -> isPurchasable(factsBySkuId.get(snapshot.getSkuId()), budgetMax))
                .map(snapshot -> toRecommendationCandidate(snapshot, factsBySkuId.get(snapshot.getSkuId())))
                .sorted(comparator)
                .toList();
    }

    private boolean isPurchasable(RecommendationCandidateLiveFact fact, Integer budgetMax) {
        return fact != null
                && fact.getAvailableStock() != null
                && fact.getAvailableStock() > 0
                && fact.getSalePrice() != null
                && (budgetMax == null
                || fact.getSalePrice().compareTo(BigDecimal.valueOf(budgetMax.longValue())) <= 0);
    }

    private RecommendationCandidate toRecommendationCandidate(
            RecommendationCandidateSnapshot snapshot,
            RecommendationCandidateLiveFact fact
    ) {
        return new RecommendationCandidate(
                snapshot.getSpuId(),
                snapshot.getSkuId(),
                snapshot.getSpuCode(),
                snapshot.getName(),
                snapshot.getCategoryName(),
                snapshot.getMainImageUrl(),
                snapshot.getFitType(),
                snapshot.getColor(),
                snapshot.getSize(),
                snapshot.getMaterials(),
                snapshot.getSeasons(),
                snapshot.getStyleTags(),
                fact.getSalePrice(),
                "in_stock",
                fact.getMinPrice(),
                fact.getMaxPrice(),
                fact.getTotalAvailableStock(),
                snapshot.getSkuCode(),
                fact.getAvailableStock(),
                snapshot.getAttributeTags()
        );
    }

    private String recommendationCandidatesCacheKey(RecommendationCandidateQuery query, boolean includeRecallText) {
        return CacheKeyConstants.recommendationCandidates(sha256Hex(canonicalRecommendationQuery(
                query, includeRecallText)));
    }

    private String canonicalRecommendationQuery(RecommendationCandidateQuery query, boolean includeRecallText) {
        return String.join("|",
                "category=" + normalizeQueryPart(query.getCategory()),
                "style=" + normalizeQueryPart(query.getStyle()),
                "season=" + normalizeQueryPart(query.getSeason()),
                "material=" + normalizeQueryPart(query.getMaterial()),
                "fit=" + normalizeQueryPart(query.getFit()),
                "gender=" + normalizeQueryPart(query.getGender()),
                "recall=" + (includeRecallText ? normalizeQueryPart(query.getRecallText()) : "")
        );
    }

    private RecommendationCandidateQuery normalizeRecommendationQuery(RecommendationCandidateQuery query) {
        if (query == null) {
            query = new RecommendationCandidateQuery();
        }
        return new RecommendationCandidateQuery(
                normalizeCategory(query.getCategory()),
                query.getStyle(),
                query.getSeason(),
                query.getMaterial(),
                query.getFit(),
                query.getBudgetMax(),
                query.getGender(),
                normalizeRecallText(query.getRecallText())
        );
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "裙子", "半裙", "半身裙", "百褶裙", "a字裙", "直筒裙" -> "半裙";
            default -> category.trim();
        };
    }

    private String normalizeQueryPart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRecallText(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void recordRecallMetrics(SnapshotLookup lookup, int candidateCount, Duration duration) {
        recordRecall(lookup.engine(), lookup.outcome(), duration);
        if (metrics != null && lookup.spuHits() != null) {
            metrics.recordRecommendationRecallSpuHits(lookup.spuHits());
        }
        if (metrics != null) {
            metrics.recordRecommendationRecallCandidates(candidateCount);
        }
    }

    private void recordRecall(String engine, String outcome, Duration duration) {
        if (metrics != null) {
            metrics.recordRecommendationRecall(engine, outcome, duration);
        }
    }

    private Duration elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    private record SnapshotLookup(
            List<RecommendationCandidateSnapshot> snapshots,
            boolean preserveSnapshotOrder,
            String engine,
            String outcome,
            Integer spuHits,
            boolean cacheable
    ) {
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is required for recommendation cache keys", exception);
        }
    }
}
