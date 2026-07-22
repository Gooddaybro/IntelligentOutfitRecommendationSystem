package com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminInventoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis boundary for admin inventory reads and stock adjustment writes.
 */
@Mapper
public interface AdminInventoryMapper {
    /**
     * Loads SKU inventory rows with their latest manual adjustment for the admin console table.
     *
     * @return SKU rows ordered by database id for stable console rendering
     */
    List<AdminInventoryRow> findInventory();

    /**
     * Reloads one SKU through the same inventory projection used by list responses after transactional writes.
     *
     * @param skuId SKU id from the route or service boundary
     * @return inventory row, or null when the SKU does not exist
     */
    AdminInventoryRow findSkuById(@Param("skuId") Long skuId);

    Integer findAvailableStockBySkuId(@Param("skuId") Long skuId);

    int updateAvailableStock(@Param("skuId") Long skuId, @Param("targetStock") int targetStock);

    int insertInventoryAdjustment(@Param("skuId") Long skuId,
                                  @Param("beforeStock") int beforeStock,
                                  @Param("afterStock") int afterStock,
                                  @Param("reason") String reason,
                                  @Param("operator") String operator);
}
