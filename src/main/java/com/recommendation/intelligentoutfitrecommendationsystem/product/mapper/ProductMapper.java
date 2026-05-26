package com.recommendation.intelligentoutfitrecommendationsystem.product.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductAttributeItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    List<ProductSearchItem> searchProducts(@Param("keyword") String keyword);

    ProductDetail findProductDetailBase(@Param("spuId") Long spuId);

    SkuSearchItem findSku(
            @Param("spuId") Long spuId,
            @Param("color") String color,
            @Param("size") String size
    );

    List<RecommendationCandidate> findRecommendationCandidates(
            @Param("query") RecommendationCandidateQuery query
    );

    List<String> findMaterials(@Param("spuId") Long spuId);

    List<String> findSeasons(@Param("spuId") Long spuId);

    List<String> findStyleTags(@Param("spuId") Long spuId);

    List<ProductAttributeItem> findAttributes(@Param("spuId") Long spuId);
}
