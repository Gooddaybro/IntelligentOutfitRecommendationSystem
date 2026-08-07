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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
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
        RecommendationCandidateQuery normalizedQuery = normalizeRecommendationQuery(query);
        if (normalizedQuery.getBudgetMax() != null && normalizedQuery.getBudgetMax() < 0) {
            throw new BadRequestException("budgetMax must not be negative");
        }
        boolean useEsRecall = shouldUseEsRecall(normalizedQuery);
        String cacheKey = recommendationCandidatesCacheKey(normalizedQuery, useEsRecall);
        var cachedSnapshots = redisCacheService.getList(cacheKey, RecommendationCandidateSnapshot.class);
        List<RecommendationCandidateSnapshot> snapshots;
        if (cachedSnapshots.isPresent()) {
            snapshots = cachedSnapshots.get();
        } else {
            snapshots = useEsRecall
                    ? findEsRecallSnapshots(normalizedQuery)
                    : productMapper.findRecommendationCandidateSnapshots(normalizedQuery);
            redisCacheService.setValue(
                    cacheKey,
                    snapshots,
                    cacheTtlProperties.recommendationCandidatesTtl()
            );
        }
        return hydrateRecommendationCandidates(snapshots, normalizedQuery.getBudgetMax(), useEsRecall);
    }

    private boolean shouldUseEsRecall(RecommendationCandidateQuery query) {
        return recallProperties.isEnabled()
                && recommendationSearchGateway != null
                && hasText(query.getRecallText());
    }

    private List<RecommendationCandidateSnapshot> findEsRecallSnapshots(RecommendationCandidateQuery query) {
        List<Long> orderedSpuIds;
        try {
            orderedSpuIds = recommendationSearchGateway.search(
                    new ProductSearchCriteria(query.getRecallText(), query.getCategory(), recallProperties.getLimit()));
        } catch (ProductSearchUnavailableException exception) {
            return productMapper.findRecommendationCandidateSnapshots(query);
        }
        if (orderedSpuIds.isEmpty()) {
            return List.of();
        }
        List<RecommendationCandidateSnapshot> snapshots =
                productMapper.findRecommendationCandidateSnapshotsBySpuIds(query, orderedSpuIds);
        return orderSnapshotsBySpuIds(snapshots, orderedSpuIds);
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
