package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderRequestFingerprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestFingerprintTests {

    private final OrderRequestFingerprint fingerprint = new OrderRequestFingerprint();

    @Test
    void cartFingerprintIgnoresSkuOrderAndDuplicates() {
        String first = fingerprint.cart(List.of(2102L, 2202L, 2102L));
        String second = fingerprint.cart(List.of(2202L, 2102L));

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void buyNowFingerprintChangesWithQuantity() {
        assertThat(fingerprint.buyNow(2102L, 1))
                .isNotEqualTo(fingerprint.buyNow(2102L, 2));
    }

    @Test
    void orderOperationIsPartOfCanonicalFingerprint() {
        assertThat(fingerprint.cart(List.of(2102L)))
                .isNotEqualTo(fingerprint.buyNow(2102L, 1));
    }
}
