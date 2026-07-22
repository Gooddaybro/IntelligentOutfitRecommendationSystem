package com.recommendation.intelligentoutfitrecommendationsystem.admin.model;

import java.math.BigDecimal;

/**
 * Write model for admin default-SKU mutations, isolating MyBatis generated-key backfill to the id property.
 */
public class AdminSkuWrite {
    private Long id;
    private final String skuCode;
    private final Long spuId;
    private final Long colorId;
    private final Long sizeId;
    private final BigDecimal salePrice;
    private final BigDecimal originalPrice;
    private final String status;

    public AdminSkuWrite(
            String skuCode,
            Long spuId,
            Long colorId,
            Long sizeId,
            BigDecimal salePrice,
            BigDecimal originalPrice,
            String status) {
        this.skuCode = skuCode;
        this.spuId = spuId;
        this.colorId = colorId;
        this.sizeId = sizeId;
        this.salePrice = salePrice;
        this.originalPrice = originalPrice;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public Long getSpuId() {
        return spuId;
    }

    public Long getColorId() {
        return colorId;
    }

    public Long getSizeId() {
        return sizeId;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public String getStatus() {
        return status;
    }
}
