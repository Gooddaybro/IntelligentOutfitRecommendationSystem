package com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * MyBatis queries for legacy admin dashboard analytics that have not yet moved to dedicated module mappers.
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
}
