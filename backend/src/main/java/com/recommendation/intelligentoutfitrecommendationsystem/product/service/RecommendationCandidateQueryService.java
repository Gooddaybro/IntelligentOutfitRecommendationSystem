package com.recommendation.intelligentoutfitrecommendationsystem.product.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheKeyConstants;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateLiveFact;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
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

    public RecommendationCandidateQueryService(
            ProductMapper productMapper,
            RedisCacheService redisCacheService,
            CacheTtlProperties cacheTtlProperties
    ) {
        this.productMapper = productMapper;
        this.redisCacheService = redisCacheService;
        this.cacheTtlProperties = cacheTtlProperties;
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
        String cacheKey = recommendationCandidatesCacheKey(normalizedQuery);
        var cachedSnapshots = redisCacheService.getList(cacheKey, RecommendationCandidateSnapshot.class);
        List<RecommendationCandidateSnapshot> snapshots;
        if (cachedSnapshots.isPresent()) {
            snapshots = cachedSnapshots.get();
        } else {
            snapshots = productMapper.findRecommendationCandidateSnapshots(normalizedQuery);
            redisCacheService.setValue(
                    cacheKey,
                    snapshots,
                    cacheTtlProperties.recommendationCandidatesTtl()
            );
        }
        return hydrateRecommendationCandidates(snapshots, normalizedQuery.getBudgetMax());
    }

    private List<RecommendationCandidate> hydrateRecommendationCandidates(
            List<RecommendationCandidateSnapshot> snapshots,
            Integer budgetMax
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
        return snapshots.stream()
                .filter(snapshot -> isPurchasable(factsBySkuId.get(snapshot.getSkuId()), budgetMax))
                .map(snapshot -> toRecommendationCandidate(snapshot, factsBySkuId.get(snapshot.getSkuId())))
                .sorted(Comparator
                        .comparing(RecommendationCandidate::getAvailableStock, Comparator.reverseOrder())
                        .thenComparing(RecommendationCandidate::getSalePrice)
                        .thenComparing(RecommendationCandidate::getSpuId)
                        .thenComparing(RecommendationCandidate::getSkuId))
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

    private String recommendationCandidatesCacheKey(RecommendationCandidateQuery query) {
        return CacheKeyConstants.recommendationCandidates(sha256Hex(canonicalRecommendationQuery(query)));
    }

    private String canonicalRecommendationQuery(RecommendationCandidateQuery query) {
        return String.join("|",
                "category=" + normalizeQueryPart(query.getCategory()),
                "style=" + normalizeQueryPart(query.getStyle()),
                "season=" + normalizeQueryPart(query.getSeason()),
                "material=" + normalizeQueryPart(query.getMaterial()),
                "fit=" + normalizeQueryPart(query.getFit()),
                "gender=" + normalizeQueryPart(query.getGender())
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
                query.getGender()
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
