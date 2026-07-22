package com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminProductWrite;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminSkuWrite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis boundary for admin product, category, default-SKU and style-tag catalog management.
 */
@Mapper
public interface AdminProductMapper {
    List<AdminProductResponse> findProducts();

    AdminProductResponse findProductById(@Param("spuId") Long spuId);

    long countProductById(@Param("spuId") Long spuId);

    int insertProduct(AdminProductWrite product);

    int updateProduct(AdminProductWrite product);

    int updateProductStatus(@Param("spuId") Long spuId, @Param("status") String status);

    int updateSkuStatusBySpuId(@Param("spuId") Long spuId, @Param("status") String status);

    List<AdminCategoryResponse> findCategories();

    AdminCategoryResponse findCategoryById(@Param("categoryId") Long categoryId);

    String findCategoryNameById(@Param("categoryId") Long categoryId);

    List<Long> findProductIdsByCategoryId(@Param("categoryId") Long categoryId);

    int updateCategory(@Param("categoryId") Long categoryId, @Param("name") String name,
                       @Param("status") String status, @Param("sortOrder") Integer sortOrder);

    Long findFirstSkuIdBySpuId(@Param("spuId") Long spuId);

    Long findFirstColorId();

    Long findFirstSizeId();

    int insertDefaultSku(AdminSkuWrite sku);

    int insertInventoryIfAbsent(@Param("skuId") Long skuId);

    int updateDefaultSku(AdminSkuWrite sku);

    int updateAllSkuStatuses(@Param("spuId") Long spuId, @Param("status") String status);

    List<String> findProductStyleTags(@Param("spuId") Long spuId);

    Long findStyleTagId(@Param("tag") String tag);

    int deleteStyleTagsBySpuId(@Param("spuId") Long spuId);

    int insertStyleTag(@Param("spuId") Long spuId, @Param("tagId") Long tagId);
}
