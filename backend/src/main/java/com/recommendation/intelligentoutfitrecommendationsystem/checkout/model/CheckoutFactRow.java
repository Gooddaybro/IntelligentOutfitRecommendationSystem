package com.recommendation.intelligentoutfitrecommendationsystem.checkout.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * CheckoutMapper 读取的内部交易事实行。
 *
 * 该可变映射对象只存在于 checkout 模块内部流程；对外返回前会转换为不可变计算结果。
 */
@Data
public class CheckoutFactRow {

    private Long skuId;

    private Long spuId;

    private String skuCode;

    private String spuCode;

    private String productName;

    private String categoryName;

    private String color;

    private String size;

    private BigDecimal salePrice;

    private Integer quantity;

    private String mainImageUrl;

    private String skuStatus;

    private String spuStatus;

    private Integer availableStock;
}
