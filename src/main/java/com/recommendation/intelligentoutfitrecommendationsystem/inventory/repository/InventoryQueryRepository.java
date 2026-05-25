package com.recommendation.intelligentoutfitrecommendationsystem.inventory.repository;

import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InventoryQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<InventoryView> findBySkuId(Long skuId) {
        String sql = """
                SELECT
                    sku.id AS sku_id,
                    sku.sku_code,
                    sku.spu_id,
                    p.name AS product_name,
                    co.name AS color,
                    so.code AS size,
                    inv.available_stock,
                    inv.locked_stock,
                    inv.sold_stock
                FROM inventory inv
                JOIN product_sku sku ON sku.id = inv.sku_id
                JOIN product_spu p ON p.id = sku.spu_id
                JOIN color co ON co.id = sku.color_id
                JOIN size_option so ON so.id = sku.size_id
                WHERE sku.id = :skuId
                """;
        List<InventoryView> results = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("skuId", skuId),
                (rs, rowNum) -> new InventoryView(
                        rs.getLong("sku_id"),
                        rs.getString("sku_code"),
                        rs.getLong("spu_id"),
                        rs.getString("product_name"),
                        rs.getString("color"),
                        rs.getString("size"),
                        rs.getInt("available_stock"),
                        rs.getInt("locked_stock"),
                        rs.getInt("sold_stock"),
                        rs.getInt("available_stock") > 0
                )
        );
        return results.stream().findFirst();
    }
}
