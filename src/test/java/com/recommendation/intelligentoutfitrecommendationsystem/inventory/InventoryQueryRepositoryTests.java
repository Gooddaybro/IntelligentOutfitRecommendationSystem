package com.recommendation.intelligentoutfitrecommendationsystem.inventory;

import com.recommendation.intelligentoutfitrecommendationsystem.inventory.repository.InventoryQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class InventoryQueryRepositoryTests {

    @Autowired
    private InventoryQueryRepository repository;

    @Test
    void findBySkuIdReturnsAvailableStock() {
        var inventory = repository.findBySkuId(2003L).orElseThrow();

        assertThat(inventory.skuCode()).isEqualTo("TS-BASIC-001-BLK-L");
        assertThat(inventory.availableStock()).isEqualTo(8);
        assertThat(inventory.inStock()).isTrue();
    }
}
