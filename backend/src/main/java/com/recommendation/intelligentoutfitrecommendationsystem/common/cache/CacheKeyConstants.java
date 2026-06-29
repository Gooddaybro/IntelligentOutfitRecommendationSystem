package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

public class CachekeyConstants {
    private CachekeyConstants() {
    }

    public static String productDetail(Long spuId) {
        return "product:detail:" + spuId;
    }
}
