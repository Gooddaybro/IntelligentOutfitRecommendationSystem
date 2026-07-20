package com.recommendation.intelligentoutfitrecommendationsystem.product.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductAttributeItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateLiveFact;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品目录数据访问入口，提供搜索、详情、SKU 和推荐候选商品查询。
 */
@Mapper
public interface ProductMapper {

    List<ProductSearchItem> searchProducts(
            @Param("keyword") String keyword,
            @Param("category") String category
    );

    List<Long> searchProductIds(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("limit") int limit
    );

    List<ProductSearchItem> findSearchItemsBySpuIds(@Param("spuIds") List<Long> spuIds);

    List<ProductSearchIndexRow> findAllSearchIndexRows();

    ProductDetail findProductDetailBase(@Param("spuId") Long spuId);

    SkuSearchItem findSku(
            @Param("spuId") Long spuId,
            @Param("color") String color,
            @Param("size") String size
    );

    List<RecommendationCandidate> findRecommendationCandidates(
            @Param("query") RecommendationCandidateQuery query
    );

    List<RecommendationCandidateSnapshot> findRecommendationCandidateSnapshots(
            @Param("query") RecommendationCandidateQuery query
    );

    List<RecommendationCandidateLiveFact> findRecommendationCandidateLiveFacts(
            @Param("skuIds") List<Long> skuIds
    );

    List<String> findMaterials(@Param("spuId") Long spuId);

    List<String> findSeasons(@Param("spuId") Long spuId);

    List<String> findStyleTags(@Param("spuId") Long spuId);

    List<ProductAttributeItem> findAttributes(@Param("spuId") Long spuId);
}
