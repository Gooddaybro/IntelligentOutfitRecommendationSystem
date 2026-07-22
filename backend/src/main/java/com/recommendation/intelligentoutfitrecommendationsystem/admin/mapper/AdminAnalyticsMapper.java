package com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsHotProduct;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminOrderTrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * MyBatis boundary for admin overview and analytics reads, keeping dashboard SQL out of services.
 */
@Mapper
public interface AdminAnalyticsMapper {
    Long countOnSaleProducts();

    Long countSkus();

    Long countLowStockSkus(@Param("threshold") int threshold);

    Long countPendingShipmentOrders();

    Long countAfterSaleOrders();

    Long countOrders();

    BigDecimal sumPaidAmount();

    Long countExposureEvents();

    Long countClickEvents();

    Long countCartEvents();

    Long countPurchasedOrders();

    List<AdminOrderTrendRow> findOrderTrendRows();

    List<AdminAnalyticsHotProduct> findAnalyticsHotProducts();

    List<AdminCategoryTrendPoint> findCategoryTrend();
}
