package com.recommendation.intelligentoutfitrecommendationsystem.product.repository;

import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ProductQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProductQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProductSearchItem> searchProducts(String keyword) {
        String sql = """
                SELECT
                    p.id AS spu_id,
                    p.spu_code,
                    p.name,
                    c.name AS category_name,
                    p.main_image_url,
                    f.name AS fit_type,
                    MIN(s.sale_price) AS min_price,
                    MAX(s.sale_price) AS max_price
                FROM product_spu p
                JOIN category c ON c.id = p.category_id
                LEFT JOIN fit_type f ON f.id = p.fit_type_id
                JOIN product_sku s ON s.spu_id = p.id
                WHERE p.status = 'on_sale'
                  AND (:keyword IS NULL OR p.name LIKE :keywordLike OR p.spu_code LIKE :keywordLike OR c.name LIKE :keywordLike)
                GROUP BY p.id, p.spu_code, p.name, c.name, p.main_image_url, f.name
                ORDER BY p.id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", blankToNull(keyword))
                .addValue("keywordLike", likeKeyword(keyword));
        return jdbcTemplate.query(sql, params, productSearchItemRowMapper());
    }

    public Optional<SkuSearchItem> findSku(Long spuId, String color, String size) {
        String sql = """
                SELECT
                    s.id AS sku_id,
                    s.sku_code,
                    s.spu_id,
                    p.name AS product_name,
                    co.name AS color,
                    so.code AS size,
                    s.sale_price,
                    s.status
                FROM product_sku s
                JOIN product_spu p ON p.id = s.spu_id
                JOIN color co ON co.id = s.color_id
                JOIN size_option so ON so.id = s.size_id
                WHERE s.spu_id = :spuId
                  AND co.name = :color
                  AND so.code = :size
                  AND s.status = 'on_sale'
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("spuId", spuId)
                .addValue("color", color)
                .addValue("size", size);
        List<SkuSearchItem> results = jdbcTemplate.query(sql, params, skuSearchItemRowMapper());
        return results.stream().findFirst();
    }

    public Optional<ProductDetail> findProductDetail(Long spuId) {
        String sql = """
                SELECT
                    p.id AS spu_id,
                    p.spu_code,
                    p.name,
                    c.name AS category_name,
                    p.description,
                    p.main_image_url,
                    f.name AS fit_type,
                    MIN(s.sale_price) AS min_price,
                    MAX(s.sale_price) AS max_price
                FROM product_spu p
                JOIN category c ON c.id = p.category_id
                LEFT JOIN fit_type f ON f.id = p.fit_type_id
                JOIN product_sku s ON s.spu_id = p.id
                WHERE p.id = :spuId
                GROUP BY p.id, p.spu_code, p.name, c.name, p.description, p.main_image_url, f.name
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("spuId", spuId);
        List<ProductDetail> results = jdbcTemplate.query(sql, params, (rs, rowNum) -> new ProductDetail(
                rs.getLong("spu_id"),
                rs.getString("spu_code"),
                rs.getString("name"),
                rs.getString("category_name"),
                rs.getString("description"),
                rs.getString("main_image_url"),
                rs.getString("fit_type"),
                findMaterials(spuId),
                findSeasons(spuId),
                findStyleTags(spuId),
                findAttributes(spuId),
                rs.getBigDecimal("min_price"),
                rs.getBigDecimal("max_price")
        ));
        return results.stream().findFirst();
    }

    public List<RecommendationCandidate> findRecommendationCandidates(
            String category,
            String style,
            String season,
            String material,
            String fit,
            Integer budgetMax
    ) {
        String sql = """
                SELECT
                    p.id AS spu_id,
                    p.spu_code,
                    p.name,
                    c.name AS category_name,
                    p.main_image_url,
                    f.name AS fit_type,
                    MIN(sku.sale_price) AS min_price,
                    MAX(sku.sale_price) AS max_price,
                    COALESCE(SUM(inv.available_stock), 0) AS total_available_stock
                FROM product_spu p
                JOIN category c ON c.id = p.category_id
                LEFT JOIN fit_type f ON f.id = p.fit_type_id
                JOIN product_sku sku ON sku.spu_id = p.id
                LEFT JOIN inventory inv ON inv.sku_id = sku.id
                WHERE p.status = 'on_sale'
                  AND (:category IS NULL OR c.name = :category)
                  AND (:fit IS NULL OR f.code = :fit)
                  AND (:style IS NULL OR EXISTS (
                        SELECT 1
                        FROM product_style_tag pst
                        JOIN style_tag st ON st.id = pst.style_tag_id
                        WHERE pst.spu_id = p.id AND st.code = :style
                  ))
                  AND (:season IS NULL OR EXISTS (
                        SELECT 1
                        FROM product_season ps
                        JOIN season se ON se.id = ps.season_id
                        WHERE ps.spu_id = p.id AND se.code = :season
                  ))
                  AND (:material IS NULL OR EXISTS (
                        SELECT 1
                        FROM product_material pm
                        JOIN material m ON m.id = pm.material_id
                        WHERE pm.spu_id = p.id AND m.name = :material
                  ))
                GROUP BY p.id, p.spu_code, p.name, c.name, p.main_image_url, f.name
                HAVING (:budgetMax IS NULL OR MIN(sku.sale_price) <= :budgetMax)
                   AND COALESCE(SUM(inv.available_stock), 0) > 0
                ORDER BY total_available_stock DESC, min_price ASC, p.id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("category", blankToNull(category))
                .addValue("style", blankToNull(style))
                .addValue("season", blankToNull(season))
                .addValue("material", blankToNull(material))
                .addValue("fit", blankToNull(fit))
                .addValue("budgetMax", budgetMax);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Long spuId = rs.getLong("spu_id");
            return new RecommendationCandidate(
                    spuId,
                    rs.getString("spu_code"),
                    rs.getString("name"),
                    rs.getString("category_name"),
                    rs.getString("main_image_url"),
                    rs.getString("fit_type"),
                    String.join(",", findMaterials(spuId)),
                    String.join(",", findSeasons(spuId)),
                    String.join(",", findStyleTags(spuId)),
                    rs.getBigDecimal("min_price"),
                    rs.getBigDecimal("max_price"),
                    rs.getInt("total_available_stock")
            );
        });
    }

    private List<String> findMaterials(Long spuId) {
        return jdbcTemplate.queryForList("""
                SELECT m.name
                FROM product_material pm
                JOIN material m ON m.id = pm.material_id
                WHERE pm.spu_id = :spuId
                ORDER BY m.id
                """, new MapSqlParameterSource("spuId", spuId), String.class);
    }

    private List<String> findSeasons(Long spuId) {
        return jdbcTemplate.queryForList("""
                SELECT se.code
                FROM product_season ps
                JOIN season se ON se.id = ps.season_id
                WHERE ps.spu_id = :spuId
                ORDER BY se.id
                """, new MapSqlParameterSource("spuId", spuId), String.class);
    }

    private List<String> findStyleTags(Long spuId) {
        return jdbcTemplate.queryForList("""
                SELECT st.code
                FROM product_style_tag pst
                JOIN style_tag st ON st.id = pst.style_tag_id
                WHERE pst.spu_id = :spuId
                ORDER BY st.id
                """, new MapSqlParameterSource("spuId", spuId), String.class);
    }

    private Map<String, String> findAttributes(Long spuId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT attr_name, attr_value
                FROM product_attribute
                WHERE spu_id = :spuId
                ORDER BY id
                """, new MapSqlParameterSource("spuId", spuId));
        Map<String, String> attributes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            attributes.put(String.valueOf(row.get("attr_name")), String.valueOf(row.get("attr_value")));
        }
        return attributes;
    }

    private RowMapper<ProductSearchItem> productSearchItemRowMapper() {
        return (rs, rowNum) -> new ProductSearchItem(
                rs.getLong("spu_id"),
                rs.getString("spu_code"),
                rs.getString("name"),
                rs.getString("category_name"),
                rs.getString("main_image_url"),
                rs.getString("fit_type"),
                rs.getBigDecimal("min_price"),
                rs.getBigDecimal("max_price")
        );
    }

    private RowMapper<SkuSearchItem> skuSearchItemRowMapper() {
        return (rs, rowNum) -> new SkuSearchItem(
                rs.getLong("sku_id"),
                rs.getString("sku_code"),
                rs.getLong("spu_id"),
                rs.getString("product_name"),
                rs.getString("color"),
                rs.getString("size"),
                rs.getBigDecimal("sale_price"),
                rs.getString("status")
        );
    }

    private String likeKeyword(String keyword) {
        String normalized = blankToNull(keyword);
        return normalized == null ? null : "%" + normalized + "%";
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
