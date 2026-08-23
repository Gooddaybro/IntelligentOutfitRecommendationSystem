package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 为订单创建意图生成稳定且不可逆的请求摘要。
 *
 * 摘要只描述客户端提交的业务意图，不混入会变化的价格和库存事实；购物车 SKU 先去重
 * 排序，并与收货地址 ID 一起摘要，使同一商品集合和地址能够安全重放而地址变更会冲突。
 */
@Component
public class OrderRequestFingerprint {

    public String cart(List<Long> skuIds, Long addressId) {
        String normalizedSkuIds = skuIds.stream()
                .distinct()
                .sorted()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return sha256Hex(
                "CART_CHECKOUT|source=CART|skuIds=" + normalizedSkuIds + "|addressId=" + addressId
        );
    }

    public String buyNow(Long skuId, Integer quantity) {
        return sha256Hex("BUY_NOW|skuId=" + skuId + "|quantity=" + quantity);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
