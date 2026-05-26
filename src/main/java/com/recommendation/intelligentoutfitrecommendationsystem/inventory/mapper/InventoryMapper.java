package com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryMapper {

    InventoryView findBySkuId(@Param("skuId") Long skuId);
}
