package com.recommendation.intelligentoutfitrecommendationsystem.admin.service;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsHotProduct;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAuditLogResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminFunnelResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminHotProduct;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminInventoryAdjustmentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminInventoryAdjustmentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminOrderResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminOverviewResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductInput;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductStatusRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminShipOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminShipmentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminSkuResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminUserResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminUserStatusRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchChangeRecorder;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Application service backing admin dashboard and product management actions.
 */
@Service
public class AdminCatalogService {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final String RANGE_LABEL = "\u6700\u8fd1 30 \u5929";
    private static final String DEFAULT_OPERATOR = "admin";
    private static final DateTimeFormatter TREND_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final Set<String> PAID_ORDER_STATUSES = Set.of("PAID", "SHIPPED", "COMPLETED");

    private final AdminMapper adminMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ProductSearchChangeRecorder productSearchChangeRecorder;

    public AdminCatalogService(
            AdminMapper adminMapper,
            JdbcTemplate jdbcTemplate,
            ProductSearchChangeRecorder productSearchChangeRecorder) {
        this.adminMapper = adminMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.productSearchChangeRecorder = productSearchChangeRecorder;
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
    public List<AdminProductResponse> listProducts() {
        return adminMapper.findProducts().stream()
                .map(this::normalizeProduct)
                .toList();
    }

    @Transactional
    public AdminProductResponse createProduct(AdminProductInput request) {
        ProductInput normalized = normalizeProductInput(request, null);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO product_spu (spu_code, name, category_id, description, main_image_url, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, normalized.spuCode());
            statement.setString(2, normalized.name());
            statement.setLong(3, normalized.categoryId());
            statement.setString(4, normalized.description());
            statement.setString(5, normalized.mainImageUrl());
            statement.setString(6, normalized.dbStatus());
            return statement;
        }, keyHolder);
        Long spuId = generatedId(keyHolder);
        createOrUpdateDefaultSku(spuId, normalized);
        replaceStyleTags(spuId, normalized.styleTags());
        insertAudit("CREATE_PRODUCT", "SPU", String.valueOf(spuId), "SUCCESS", normalized.spuCode());
        // 与商品写入处于同一事务，避免数据库成功但同步事件丢失。
        productSearchChangeRecorder.record(spuId);
        return requireProduct(spuId);
    }

    @Transactional
    public AdminProductResponse updateProduct(Long spuId, AdminProductInput request) {
        if (spuId == null || spuId <= 0) {
            throw new BadRequestException("spuId must be positive");
        }
        ProductInput normalized = normalizeProductInput(request, spuId);
        int updated = jdbcTemplate.update("""
                UPDATE product_spu
                SET spu_code = ?, name = ?, category_id = ?, description = ?, main_image_url = ?,
                    status = ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """, normalized.spuCode(), normalized.name(), normalized.categoryId(), normalized.description(),
                normalized.mainImageUrl(), normalized.dbStatus(), spuId);
        if (updated == 0) {
            throw new ResourceNotFoundException("product not found");
        }
        createOrUpdateDefaultSku(spuId, normalized);
        replaceStyleTags(spuId, normalized.styleTags());
        insertAudit("UPDATE_PRODUCT", "SPU", String.valueOf(spuId), "SUCCESS", normalized.spuCode());
        productSearchChangeRecorder.record(spuId);
        return requireProduct(spuId);
    }

    @Transactional
    public AdminProductResponse changeProductStatus(Long spuId, AdminProductStatusRequest request) {
        String dbStatus = toDbStatus(request == null ? null : request.status());
        int updated = adminMapper.updateProductStatus(spuId, dbStatus);
        if (updated == 0) {
            throw new ResourceNotFoundException("product not found");
        }
        jdbcTemplate.update("UPDATE product_sku SET status = ? WHERE spu_id = ?", toSkuStatus(dbStatus), spuId);
        insertAudit("CHANGE_PRODUCT_STATUS", "SPU", String.valueOf(spuId), "SUCCESS", toApiStatus(dbStatus));
        productSearchChangeRecorder.record(spuId);
        return requireProduct(spuId);
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listCategories() {
        return jdbcTemplate.query("""
                SELECT c.id, c.name, c.parent_id, c.level, c.sort_order,
                       CASE WHEN c.status = 'active' THEN TRUE ELSE FALSE END AS enabled,
                       COUNT(p.id) AS product_count
                FROM category c
                LEFT JOIN product_spu p ON p.category_id = c.id
                GROUP BY c.id, c.name, c.parent_id, c.level, c.sort_order, c.status
                ORDER BY c.sort_order ASC, c.id ASC
                """, this::mapCategory);
    }

    @Transactional
    public AdminCategoryResponse updateCategory(Long categoryId, AdminCategoryRequest request) {
        if (categoryId == null || categoryId <= 0) {
            throw new BadRequestException("category id must be positive");
        }
        if (request != null && categoryId.equals(request.parentId())) {
            throw new BadRequestException("category cannot be its own parent");
        }
        String existingName = queryNullable("SELECT name FROM category WHERE id = ?", String.class, categoryId);
        if (existingName == null) {
            throw new ResourceNotFoundException("category not found");
        }
        String requestedName = request == null ? null : trimToNull(request.name());
        String updatedName = requestedName == null ? existingName : requestedName;
        List<Long> affectedSpuIds = existingName.equals(updatedName)
                ? List.of()
                : jdbcTemplate.queryForList(
                        "SELECT id FROM product_spu WHERE category_id = ? ORDER BY id", Long.class, categoryId);
        boolean enabled = request == null || request.enabled() == null || request.enabled();
        if (request != null && request.sortOrder() != null) {
            jdbcTemplate.update("""
                    UPDATE category
                    SET name = ?, status = ?, sort_order = ?, updated_at = CURRENT_TIMESTAMP(6)
                    WHERE id = ?
                    """, updatedName, enabled ? "active" : "inactive", request.sortOrder(), categoryId);
        } else {
            jdbcTemplate.update("""
                    UPDATE category
                    SET name = ?, status = ?, updated_at = CURRENT_TIMESTAMP(6)
                    WHERE id = ?
                    """, updatedName, enabled ? "active" : "inactive", categoryId);
        }
        AdminCategoryResponse category = findCategory(categoryId);
        insertAudit(enabled ? "ENABLE_CATEGORY" : "DISABLE_CATEGORY", "CATEGORY", String.valueOf(categoryId),
                "SUCCESS", category.name());
        // 分类名会进入搜索文档，因此仅在名称变化时为该分类下商品批量生成同步事件。
        affectedSpuIds.forEach(productSearchChangeRecorder::record);
        return category;
    }

    @Transactional(readOnly = true)
    public List<AdminSkuResponse> listInventory() {
        return jdbcTemplate.query(inventorySql(null), this::mapSku);
    }

    @Transactional
    public AdminSkuResponse adjustInventory(Long skuId, AdminInventoryAdjustmentRequest request) {
        if (skuId == null || skuId <= 0) {
            throw new BadRequestException("skuId must be positive");
        }
        int targetStock = request == null || request.targetStock() == null ? -1 : request.targetStock();
        if (targetStock < 0) {
            throw new BadRequestException("targetStock must be non-negative");
        }
        String reason = normalizeRequiredText(request == null ? null : request.reason(), "reason");
        Integer beforeStock = queryNullable("SELECT available_stock FROM inventory WHERE sku_id = ?", Integer.class, skuId);
        if (beforeStock == null) {
            throw new ResourceNotFoundException("sku not found");
        }
        jdbcTemplate.update("""
                UPDATE inventory
                SET available_stock = ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE sku_id = ?
                """, targetStock, skuId);
        jdbcTemplate.update("""
                INSERT INTO admin_inventory_adjustment (sku_id, before_stock, after_stock, reason, operator)
                VALUES (?, ?, ?, ?, ?)
                """, skuId, beforeStock, targetStock, reason, DEFAULT_OPERATOR);
        insertAudit("ADJUST_STOCK", "SKU", String.valueOf(skuId), "SUCCESS", beforeStock + " -> " + targetStock);
        return findSku(skuId);
    }

    @Transactional(readOnly = true)
    public List<AdminOrderResponse> listOrders() {
        return jdbcTemplate.query(orderSql(null), this::mapOrder);
    }

    @Transactional
    public AdminOrderResponse shipOrder(String orderNo, AdminShipOrderRequest request) {
        String normalizedOrderNo = normalizeRequiredText(orderNo, "orderNo");
        String carrier = normalizeRequiredText(request == null ? null : request.carrier(), "carrier");
        String trackingNo = normalizeRequiredText(request == null ? null : request.trackingNo(), "trackingNo");
        Map<String, Object> order = queryMap("SELECT id, status FROM sales_order WHERE order_no = ?", normalizedOrderNo);
        if (order == null) {
            throw new ResourceNotFoundException("order not found");
        }
        String status = String.valueOf(order.get("status"));
        if (!"PAID".equals(status)) {
            throw new BadRequestException("order cannot be shipped");
        }
        Long orderId = ((Number) order.get("id")).longValue();
        jdbcTemplate.update("UPDATE sales_order SET status = 'SHIPPED', updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                orderId);
        jdbcTemplate.update("DELETE FROM order_shipment WHERE order_no = ?", normalizedOrderNo);
        jdbcTemplate.update("""
                INSERT INTO order_shipment (order_id, order_no, carrier, tracking_no)
                VALUES (?, ?, ?, ?)
                """, orderId, normalizedOrderNo, carrier, trackingNo);
        insertAudit("SHIP_ORDER", "ORDER", normalizedOrderNo, "SUCCESS", carrier + " " + trackingNo);
        return findOrder(normalizedOrderNo);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return jdbcTemplate.query(usersSql(null), this::mapUser);
    }

    @Transactional
    public AdminUserResponse changeUserStatus(Long userId, AdminUserStatusRequest request) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
        String dbStatus = toDbUserStatus(request == null ? null : request.status());
        int updated = jdbcTemplate.update("UPDATE user_account SET status = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                dbStatus, userId);
        if (updated == 0) {
            throw new ResourceNotFoundException("user not found");
        }
        AdminUserResponse user = findUser(userId);
        insertAudit("disabled".equals(dbStatus) ? "DISABLE_USER" : "ENABLE_USER", "USER", String.valueOf(userId),
                "SUCCESS", user.username());
        return user;
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

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> listAuditLogs() {
        return jdbcTemplate.query("""
                SELECT id, operator, action, target_type, target_id, result, summary, created_at
                FROM admin_audit_log
                ORDER BY created_at DESC, id DESC
                LIMIT 200
                """, this::mapAuditLog);
    }

    private AdminProductResponse requireProduct(Long spuId) {
        AdminProductResponse product = adminMapper.findProductById(spuId);
        if (product == null) {
            throw new ResourceNotFoundException("product not found");
        }
        return normalizeProduct(product);
    }

    private AdminProductResponse normalizeProduct(AdminProductResponse product) {
        product.setStatus(toApiStatus(product.getStatus()));
        product.setStyleTags(findProductStyleTags(product.getSpuId()));
        return product;
    }

    private ProductInput normalizeProductInput(AdminProductInput request, Long spuId) {
        if (request == null) {
            throw new BadRequestException("product payload is required");
        }
        String spuCode = normalizeRequiredText(request.spuCode(), "spuCode");
        String name = normalizeRequiredText(request.name(), "name");
        Long categoryId = request.categoryId();
        if (categoryId == null || categoryId <= 0) {
            throw new BadRequestException("categoryId must be positive");
        }
        if (spuId != null && zero(queryNullable("SELECT COUNT(*) FROM product_spu WHERE id = ?", Long.class, spuId)) == 0) {
            throw new ResourceNotFoundException("product not found");
        }
        String dbStatus = toDbStatus(request.status() == null ? "DRAFT" : request.status());
        BigDecimal minPrice = nonNegativeMoney(request.minPrice(), "minPrice");
        BigDecimal maxPrice = nonNegativeMoney(request.maxPrice() == null ? minPrice : request.maxPrice(), "maxPrice");
        if (maxPrice.compareTo(minPrice) < 0) {
            maxPrice = minPrice;
        }
        return new ProductInput(
                spuCode,
                name,
                categoryId,
                trimToNull(request.mainImageUrl()),
                minPrice,
                maxPrice,
                dbStatus,
                trimToNull(request.description()),
                request.styleTags() == null ? List.of() : request.styleTags().stream()
                        .map(this::trimToNull)
                        .filter(value -> value != null && !value.isBlank())
                        .toList()
        );
    }

    private void createOrUpdateDefaultSku(Long spuId, ProductInput input) {
        Long firstSkuId = queryNullable("SELECT MIN(id) FROM product_sku WHERE spu_id = ?", Long.class, spuId);
        String skuStatus = toSkuStatus(input.dbStatus());
        if (firstSkuId == null) {
            Long colorId = queryNullable("SELECT id FROM color ORDER BY id LIMIT 1", Long.class);
            Long sizeId = queryNullable("SELECT id FROM size_option ORDER BY id LIMIT 1", Long.class);
            if (colorId == null || sizeId == null) {
                throw new BadRequestException("default color or size is missing");
            }
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO product_sku (sku_code, spu_id, color_id, size_id, sale_price, original_price, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, defaultSkuCode(input.spuCode()));
                statement.setLong(2, spuId);
                statement.setLong(3, colorId);
                statement.setLong(4, sizeId);
                statement.setBigDecimal(5, input.minPrice());
                statement.setBigDecimal(6, input.maxPrice());
                statement.setString(7, skuStatus);
                return statement;
            }, keyHolder);
            jdbcTemplate.update("INSERT INTO inventory (sku_id, available_stock, locked_stock, sold_stock) VALUES (?, 0, 0, 0)",
                    generatedId(keyHolder));
            return;
        }
        jdbcTemplate.update("""
                UPDATE product_sku
                SET sale_price = ?, original_price = ?, status = ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """, input.minPrice(), input.maxPrice(), skuStatus, firstSkuId);
        jdbcTemplate.update("UPDATE product_sku SET status = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE spu_id = ?",
                skuStatus, spuId);
    }

    private void replaceStyleTags(Long spuId, List<String> styleTags) {
        jdbcTemplate.update("DELETE FROM product_style_tag WHERE spu_id = ?", spuId);
        for (String tag : styleTags) {
            Long tagId = queryNullable("SELECT id FROM style_tag WHERE code = ? OR name = ? ORDER BY id LIMIT 1",
                    Long.class, tag, tag);
            if (tagId != null) {
                jdbcTemplate.update("INSERT INTO product_style_tag (spu_id, style_tag_id) VALUES (?, ?)", spuId, tagId);
            }
        }
    }

    private List<String> findProductStyleTags(Long spuId) {
        return jdbcTemplate.query("""
                SELECT st.code
                FROM product_style_tag pst
                JOIN style_tag st ON st.id = pst.style_tag_id
                WHERE pst.spu_id = ?
                ORDER BY st.id
                """, (rs, rowNum) -> rs.getString("code"), spuId);
    }

    private AdminCategoryResponse findCategory(Long categoryId) {
        return singleOrNotFound(jdbcTemplate.query("""
                SELECT c.id, c.name, c.parent_id, c.level, c.sort_order,
                       CASE WHEN c.status = 'active' THEN TRUE ELSE FALSE END AS enabled,
                       COUNT(p.id) AS product_count
                FROM category c
                LEFT JOIN product_spu p ON p.category_id = c.id
                WHERE c.id = ?
                GROUP BY c.id, c.name, c.parent_id, c.level, c.sort_order, c.status
                """, this::mapCategory, categoryId), "category not found");
    }

    private AdminSkuResponse findSku(Long skuId) {
        return singleOrNotFound(jdbcTemplate.query(inventorySql("s.id = ?"), this::mapSku, skuId), "sku not found");
    }

    private AdminOrderResponse findOrder(String orderNo) {
        return singleOrNotFound(jdbcTemplate.query(orderSql("o.order_no = ?"), this::mapOrder, orderNo), "order not found");
    }

    private AdminUserResponse findUser(Long userId) {
        return singleOrNotFound(jdbcTemplate.query(usersSql("u.id = ?"), this::mapUser, userId), "user not found");
    }

    private String inventorySql(String condition) {
        String whereClause = condition == null ? "" : " WHERE " + condition;
        return """
                SELECT s.id AS sku_id, s.sku_code, s.spu_id, p.name AS product_name,
                       co.name AS color, so.code AS size, s.sale_price, inv.available_stock,
                       CASE WHEN s.status = 'on_sale' THEN 'ACTIVE' ELSE 'INACTIVE' END AS status,
                       adj.before_stock, adj.after_stock, adj.reason, adj.operator, adj.adjusted_at
                FROM product_sku s
                JOIN product_spu p ON p.id = s.spu_id
                JOIN color co ON co.id = s.color_id
                JOIN size_option so ON so.id = s.size_id
                LEFT JOIN inventory inv ON inv.sku_id = s.id
                LEFT JOIN admin_inventory_adjustment adj ON adj.id = (
                    SELECT MAX(inner_adj.id)
                    FROM admin_inventory_adjustment inner_adj
                    WHERE inner_adj.sku_id = s.id
                )
                """ + whereClause + " ORDER BY s.id ASC";
    }

    private String orderSql(String condition) {
        String whereClause = condition == null ? "" : " WHERE " + condition;
        return """
                SELECT o.id, o.order_no, u.username, o.status,
                       CASE
                           WHEN EXISTS (SELECT 1 FROM payment pay WHERE pay.order_id = o.id AND pay.status = 'SUCCESS') THEN 'PAID'
                           WHEN o.status = 'UNPAID' THEN 'UNPAID'
                           ELSE o.status
                       END AS payment_status,
                       o.total_amount, COALESCE(SUM(oi.quantity), 0) AS item_count, o.created_at,
                       os.carrier, os.tracking_no
                FROM sales_order o
                JOIN user_account u ON u.id = o.user_id
                LEFT JOIN order_item oi ON oi.order_id = o.id
                LEFT JOIN order_shipment os ON os.order_id = o.id
                """ + whereClause + """
                GROUP BY o.id, o.order_no, u.username, o.status, o.total_amount, o.created_at, os.carrier, os.tracking_no
                ORDER BY o.created_at DESC, o.id DESC
                """;
    }

    private String usersSql(String condition) {
        String whereClause = condition == null ? "" : " WHERE " + condition;
        return """
                SELECT u.id AS user_id, u.username, up.nickname, u.email, u.phone,
                       CASE WHEN u.status = 'disabled' THEN 'DISABLED' ELSE 'ACTIVE' END AS status,
                       u.created_at AS registered_at,
                       COUNT(o.id) AS order_count,
                       COALESCE(SUM(CASE WHEN o.status IN ('PAID', 'SHIPPED', 'COMPLETED') THEN o.total_amount ELSE 0 END), 0) AS paid_amount
                FROM user_account u
                LEFT JOIN user_profile up ON up.user_id = u.id
                LEFT JOIN sales_order o ON o.user_id = u.id
                """ + whereClause + """
                GROUP BY u.id, u.username, up.nickname, u.email, u.phone, u.status, u.created_at
                ORDER BY u.id ASC
                """;
    }

    private AdminCategoryResponse mapCategory(ResultSet resultSet, int rowNum) throws SQLException {
        return new AdminCategoryResponse(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                nullableLong(resultSet, "parent_id"),
                resultSet.getInt("level"),
                resultSet.getInt("sort_order"),
                resultSet.getBoolean("enabled"),
                resultSet.getLong("product_count")
        );
    }

    private AdminSkuResponse mapSku(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp adjustedAt = resultSet.getTimestamp("adjusted_at");
        AdminInventoryAdjustmentResponse adjustment = adjustedAt == null ? null : new AdminInventoryAdjustmentResponse(
                resultSet.getInt("before_stock"),
                resultSet.getInt("after_stock"),
                resultSet.getString("reason"),
                resultSet.getString("operator"),
                adjustedAt.toLocalDateTime()
        );
        return new AdminSkuResponse(
                resultSet.getLong("sku_id"),
                resultSet.getString("sku_code"),
                resultSet.getLong("spu_id"),
                resultSet.getString("product_name"),
                resultSet.getString("color"),
                resultSet.getString("size"),
                resultSet.getBigDecimal("sale_price"),
                resultSet.getInt("available_stock"),
                LOW_STOCK_THRESHOLD,
                resultSet.getString("status"),
                adjustment
        );
    }

    private AdminOrderResponse mapOrder(ResultSet resultSet, int rowNum) throws SQLException {
        String carrier = resultSet.getString("carrier");
        AdminShipmentResponse shipment = carrier == null ? null
                : new AdminShipmentResponse(carrier, resultSet.getString("tracking_no"));
        String status = resultSet.getString("status");
        return new AdminOrderResponse(
                resultSet.getString("order_no"),
                resultSet.getString("username"),
                status,
                resultSet.getString("payment_status"),
                resultSet.getBigDecimal("total_amount"),
                resultSet.getLong("item_count"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                availableActions(status),
                null,
                shipment
        );
    }

    private AdminUserResponse mapUser(ResultSet resultSet, int rowNum) throws SQLException {
        return new AdminUserResponse(
                resultSet.getLong("user_id"),
                resultSet.getString("username"),
                resultSet.getString("nickname"),
                resultSet.getString("email"),
                resultSet.getString("phone"),
                resultSet.getString("status"),
                resultSet.getTimestamp("registered_at").toLocalDateTime(),
                resultSet.getLong("order_count"),
                resultSet.getBigDecimal("paid_amount")
        );
    }

    private AdminAuditLogResponse mapAuditLog(ResultSet resultSet, int rowNum) throws SQLException {
        return new AdminAuditLogResponse(
                resultSet.getLong("id"),
                resultSet.getString("operator"),
                resultSet.getString("action"),
                resultSet.getString("target_type"),
                resultSet.getString("target_id"),
                resultSet.getString("result"),
                resultSet.getString("summary"),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private List<String> availableActions(String status) {
        if ("PAID".equals(status)) {
            return List.of("SHIP");
        }
        if ("UNPAID".equals(status)) {
            return List.of("CANCEL");
        }
        if ("SHIPPED".equals(status) || "COMPLETED".equals(status)) {
            return List.of("AFTER_SALE");
        }
        return List.of();
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

    private void insertAudit(String action, String targetType, String targetId, String result, String summary) {
        jdbcTemplate.update("""
                INSERT INTO admin_audit_log (operator, action, target_type, target_id, result, summary)
                VALUES (?, ?, ?, ?, ?, ?)
                """, DEFAULT_OPERATOR, action, targetType, targetId, result, summary == null ? "" : summary);
    }

    private String toDbStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("status is required");
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ON_SALE" -> "on_sale";
            case "OFF_SHELF", "OFF_SALE" -> "off_sale";
            case "DRAFT" -> "draft";
            case "DELETED" -> "deleted";
            default -> throw new BadRequestException("unsupported product status");
        };
    }

    private String toApiStatus(String dbStatus) {
        if (dbStatus == null) {
            return null;
        }
        return switch (dbStatus.toLowerCase(Locale.ROOT)) {
            case "on_sale" -> "ON_SALE";
            case "off_sale", "off_shelf" -> "OFF_SHELF";
            case "draft" -> "DRAFT";
            case "deleted" -> "DELETED";
            default -> dbStatus.toUpperCase(Locale.ROOT);
        };
    }

    private String toSkuStatus(String dbProductStatus) {
        return "on_sale".equals(dbProductStatus) ? "on_sale" : "off_sale";
    }

    private String toDbUserStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("status is required");
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> "active";
            case "DISABLED" -> "disabled";
            default -> throw new BadRequestException("unsupported user status");
        };
    }

    private String normalizeRequiredText(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(field + " is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal nonNegativeMoney(BigDecimal value, String field) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(field + " must be non-negative");
        }
        return normalized;
    }

    private String defaultSkuCode(String spuCode) {
        String prefix = spuCode.length() > 52 ? spuCode.substring(0, 52) : spuCode;
        return prefix + "-DEFAULT";
    }

    private Long generatedId(KeyHolder keyHolder) {
        try {
            if (keyHolder.getKey() != null) {
                return keyHolder.getKey().longValue();
            }
        } catch (DataAccessException ignored) {
            // Some drivers return multiple generated columns. Fall back to the first numeric key.
        }
        if (keyHolder.getKeys() != null) {
            for (Object value : keyHolder.getKeys().values()) {
                if (value instanceof Number number) {
                    return number.longValue();
                }
            }
        }
        throw new IllegalStateException("generated key is missing");
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private <T> T queryNullable(String sql, Class<T> type, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, type, args);
        } catch (DataAccessException exception) {
            return null;
        }
    }

    private Map<String, Object> queryMap(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForMap(sql, args);
        } catch (DataAccessException exception) {
            return null;
        }
    }

    private long safeCount(String sql) {
        return zero(queryNullable(sql, Long.class));
    }

    private <T> T singleOrNotFound(List<T> items, String message) {
        if (items.isEmpty()) {
            throw new ResourceNotFoundException(message);
        }
        return items.getFirst();
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Normalized product payload used inside the admin service transaction.
     */
    private record ProductInput(
            String spuCode,
            String name,
            Long categoryId,
            String mainImageUrl,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String dbStatus,
            String description,
            List<String> styleTags
    ) {
    }
}
