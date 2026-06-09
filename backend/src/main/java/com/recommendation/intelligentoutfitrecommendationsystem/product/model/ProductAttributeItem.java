package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品扩展属性条目，用于把多值属性查询结果装配为商品详情属性映射。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductAttributeItem {
    private String attrName;
    private String attrValue;
}
