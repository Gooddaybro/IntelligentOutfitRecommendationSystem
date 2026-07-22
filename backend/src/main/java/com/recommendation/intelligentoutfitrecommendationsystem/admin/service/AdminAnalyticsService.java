package com.recommendation.intelligentoutfitrecommendationsystem.admin.service;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminFunnelResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminHotProduct;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminOverviewResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminAnalyticsMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminOrderTrendRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service boundary for admin operating metrics, keeping SQL in MyBatis while preserving dashboard DTO contracts.
 */
@Service
public class AdminAnalyticsService {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final String RANGE_LABEL = "\u6700\u8fd1 30 \u5929";
    private static final DateTimeFormatter TREND_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final Set<String> PAID_ORDER_STATUSES = Set.of("PAID", "SHIPPED", "COMPLETED");

    private final AdminAnalyticsMapper adminAnalyticsMapper;

    public AdminAnalyticsService(AdminAnalyticsMapper adminAnalyticsMapper) {
        this.adminAnalyticsMapper = adminAnalyticsMapper;
    }

    /**
     * Assembles the management-console overview without exposing mapper rows to the controller contract.
     *
     * @return overview metrics with null database aggregates normalized to zero
     */
    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        return new AdminOverviewResponse(
                zero(adminAnalyticsMapper.countOnSaleProducts()),
                zero(adminAnalyticsMapper.countSkus()),
                zero(adminAnalyticsMapper.countLowStockSkus(LOW_STOCK_THRESHOLD)),
                zero(adminAnalyticsMapper.countPendingShipmentOrders()),
                zero(adminAnalyticsMapper.countAfterSaleOrders()),
                zero(adminAnalyticsMapper.countOrders()),
                zero(adminAnalyticsMapper.sumPaidAmount()),
                RANGE_LABEL,
                findOverviewTrend(),
                findOverviewHotProducts()
        );
    }

    /**
     * Assembles the analytics page from fixed mapper queries so funnel SQL cannot be injected by callers.
     *
     * @return analytics metrics with the existing admin JSON field order and zero normalization
     */
    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAnalytics() {
        return new AdminAnalyticsResponse(
                RANGE_LABEL,
                zero(adminAnalyticsMapper.countOrders()),
                zero(adminAnalyticsMapper.sumPaidAmount()),
                getFunnel(),
                findAnalyticsTrend(),
                adminAnalyticsMapper.findAnalyticsHotProducts(),
                adminAnalyticsMapper.findCategoryTrend()
        );
    }

    private AdminFunnelResponse getFunnel() {
        return new AdminFunnelResponse(
                zero(adminAnalyticsMapper.countExposureEvents()),
                zero(adminAnalyticsMapper.countClickEvents()),
                zero(adminAnalyticsMapper.countCartEvents()),
                zero(adminAnalyticsMapper.countPurchasedOrders()),
                "Exposure, click, cart and purchase counts are based on behavior events and paid orders.");
    }

    private List<AdminAnalyticsTrendPoint> findAnalyticsTrend() {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (AdminOrderTrendRow row : adminAnalyticsMapper.findOrderTrendRows()) {
            String label = row.createdAt().format(TREND_LABEL_FORMATTER);
            counts.put(label, counts.getOrDefault(label, 0L) + 1L);
            BigDecimal current = amounts.getOrDefault(label, BigDecimal.ZERO);
            if (PAID_ORDER_STATUSES.contains(row.status())) {
                current = current.add(zero(row.totalAmount()));
            }
            amounts.put(label, current);
        }
        List<AdminAnalyticsTrendPoint> trend = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            trend.add(new AdminAnalyticsTrendPoint(entry.getKey(), entry.getValue(), amounts.get(entry.getKey())));
        }
        return trend;
    }

    private List<AdminTrendPoint> findOverviewTrend() {
        return findAnalyticsTrend().stream()
                .map(point -> new AdminTrendPoint(point.label(), point.paidAmount()))
                .toList();
    }

    private List<AdminHotProduct> findOverviewHotProducts() {
        return adminAnalyticsMapper.findAnalyticsHotProducts().stream()
                .map(product -> new AdminHotProduct(product.spuId(), product.name(), product.sales()))
                .toList();
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
