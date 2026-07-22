package com.recommendation.intelligentoutfitrecommendationsystem.admin.service;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsHotProduct;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminFunnelResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminHotProduct;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminOverviewResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Application service backing admin dashboard analytics until the remaining dashboard queries move to dedicated mappers.
 */
@Service
public class AdminCatalogService {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final String RANGE_LABEL = "\u6700\u8fd1 30 \u5929";
    private static final DateTimeFormatter TREND_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final Set<String> PAID_ORDER_STATUSES = Set.of("PAID", "SHIPPED", "COMPLETED");

    private final AdminMapper adminMapper;
    private final JdbcTemplate jdbcTemplate;

    public AdminCatalogService(AdminMapper adminMapper, JdbcTemplate jdbcTemplate) {
        this.adminMapper = adminMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        return new AdminOverviewResponse(
                zero(adminMapper.countOnSaleProducts()),
                zero(adminMapper.countSkus()),
                zero(adminMapper.countLowStockSkus(LOW_STOCK_THRESHOLD)),
                zero(adminMapper.countPendingShipmentOrders()),
                zero(adminMapper.countAfterSaleOrders()),
                zero(adminMapper.countOrders()),
                zero(adminMapper.sumPaidAmount()),
                RANGE_LABEL,
                findOverviewTrend(),
                findOverviewHotProducts()
        );
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAnalytics() {
        return new AdminAnalyticsResponse(
                RANGE_LABEL,
                zero(queryNullable("SELECT COUNT(*) FROM sales_order", Long.class)),
                zero(queryNullable("""
                        SELECT COALESCE(SUM(total_amount), 0)
                        FROM sales_order
                        WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')
                        """, BigDecimal.class)),
                getFunnel(),
                findAnalyticsTrend(),
                findAnalyticsHotProducts(),
                findCategoryTrend()
        );
    }

    private AdminFunnelResponse getFunnel() {
        long exposed = safeCount("SELECT COUNT(*) FROM behavior_event WHERE LOWER(event_type) LIKE '%expos%'");
        long clicked = safeCount("SELECT COUNT(*) FROM behavior_event WHERE LOWER(event_type) LIKE '%click%'");
        long cartAdded = safeCount("SELECT COUNT(*) FROM behavior_event WHERE LOWER(event_type) LIKE '%cart%'");
        long purchased = zero(queryNullable("""
                SELECT COUNT(*)
                FROM sales_order
                WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')
                """, Long.class));
        return new AdminFunnelResponse(exposed, clicked, cartAdded, purchased,
                "Exposure, click, cart and purchase counts are based on behavior events and paid orders.");
    }

    private List<AdminAnalyticsTrendPoint> findAnalyticsTrend() {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT status, total_amount, created_at FROM sales_order ORDER BY created_at ASC, id ASC",
                resultSet -> {
                    String label = resultSet.getTimestamp("created_at").toLocalDateTime().format(TREND_LABEL_FORMATTER);
                    counts.put(label, counts.getOrDefault(label, 0L) + 1L);
                    BigDecimal current = amounts.getOrDefault(label, BigDecimal.ZERO);
                    String status = resultSet.getString("status");
                    if (PAID_ORDER_STATUSES.contains(status)) {
                        current = current.add(resultSet.getBigDecimal("total_amount"));
                    }
                    amounts.put(label, current);
                });
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

    private List<AdminAnalyticsHotProduct> findAnalyticsHotProducts() {
        return jdbcTemplate.query("""
                SELECT oi.spu_id, p.name, SUM(oi.quantity) AS sales, COALESCE(SUM(oi.line_amount), 0) AS paid_amount
                FROM order_item oi
                JOIN sales_order o ON o.id = oi.order_id
                LEFT JOIN product_spu p ON p.id = oi.spu_id
                WHERE o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
                GROUP BY oi.spu_id, p.name
                ORDER BY sales DESC, paid_amount DESC, oi.spu_id ASC
                LIMIT 10
                """, (rs, rowNum) -> new AdminAnalyticsHotProduct(
                rs.getLong("spu_id"),
                rs.getString("name"),
                rs.getLong("sales"),
                rs.getBigDecimal("paid_amount")
        ));
    }

    private List<AdminHotProduct> findOverviewHotProducts() {
        return findAnalyticsHotProducts().stream()
                .map(product -> new AdminHotProduct(product.spuId(), product.name(), product.sales()))
                .toList();
    }

    private List<AdminCategoryTrendPoint> findCategoryTrend() {
        return jdbcTemplate.query("""
                SELECT oi.category_name, SUM(oi.quantity) AS sales
                FROM order_item oi
                JOIN sales_order o ON o.id = oi.order_id
                WHERE o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
                GROUP BY oi.category_name
                ORDER BY sales DESC, oi.category_name ASC
                LIMIT 10
                """, (rs, rowNum) -> new AdminCategoryTrendPoint(
                rs.getString("category_name"),
                rs.getLong("sales")
        ));
    }

    private <T> T queryNullable(String sql, Class<T> type, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, type, args);
        } catch (DataAccessException exception) {
            return null;
        }
    }

    private long safeCount(String sql) {
        return zero(queryNullable(sql, Long.class));
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
