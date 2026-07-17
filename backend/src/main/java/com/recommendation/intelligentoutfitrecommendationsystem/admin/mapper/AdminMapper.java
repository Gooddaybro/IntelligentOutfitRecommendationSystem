package com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * MyBatis queries for admin dashboard and product management data.
 */
@Mapper
public interface AdminMapper {
    Long countOnSaleProducts();

    Long countSkus();

    Long countLowStockSkus(@Param("threshold") int threshold);

    Long countPendingShipmentOrders();

    Long countAfterSaleOrders();

    Long countOrders();

    BigDecimal sumPaidAmount();

    List<AdminProductResponse> findProducts();

    AdminProductResponse findProductById(@Param("spuId") Long spuId);

    int updateProductStatus(@Param("spuId") Long spuId, @Param("status") String status);
}
