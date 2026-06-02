package com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 库存查询数据访问入口，为商城接口和 Python AI 推荐服务提供 SKU 可售状态。
 */
@Mapper
public interface InventoryMapper {

    InventoryView findBySkuId(@Param("skuId") Long skuId);

    /**
     * 原子锁定订单库存。
     *
     * @param skuId 下单 SKU
     * @param quantity 需要锁定的数量
     * @return 1 表示库存充足并锁定成功，0 表示库存不足或库存记录不存在
     */
    int lockStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
