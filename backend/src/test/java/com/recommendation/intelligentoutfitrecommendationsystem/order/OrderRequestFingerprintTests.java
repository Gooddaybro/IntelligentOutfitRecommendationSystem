package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderRequestFingerprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestFingerprintTests {

    private final OrderRequestFingerprint fingerprint = new OrderRequestFingerprint();

    @Test
    void cartFingerprintIgnoresSkuOrderAndDuplicates() {
        String first = fingerprint.cart(List.of(2102L, 2202L, 2102L), 7L);
        String second = fingerprint.cart(List.of(2202L, 2102L), 7L);

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void cartFingerprintChangesWithAddress() {
        assertThat(fingerprint.cart(List.of(2102L, 2202L), 7L))
                .isNotEqualTo(fingerprint.cart(List.of(2102L, 2202L), 8L));
    }

    @Test
    void cartFingerprintChangesWithSkuSelection() {
        assertThat(fingerprint.cart(List.of(2102L, 2202L), 7L))
                .isNotEqualTo(fingerprint.cart(List.of(2102L), 7L));
    }

    @Test
    void buyNowFingerprintChangesWithQuantity() {
        assertThat(fingerprint.buyNow(2102L, 1))
                .isNotEqualTo(fingerprint.buyNow(2102L, 2));
    }

    @Test
    void orderOperationIsPartOfCanonicalFingerprint() {
        assertThat(fingerprint.cart(List.of(2102L), 7L))
                .isNotEqualTo(fingerprint.buyNow(2102L, 1));
    }
}
