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
}
