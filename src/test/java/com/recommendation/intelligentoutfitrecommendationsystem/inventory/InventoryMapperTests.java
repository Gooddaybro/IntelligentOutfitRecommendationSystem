package com.recommendation.intelligentoutfitrecommendationsystem.inventory;

import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class InventoryMapperTests {

    @Autowired
    private InventoryMapper mapper;

    @Test
    void findBySkuIdReturnsAvailableStock() {
        var inventory = mapper.findBySkuId(2003L);

        assertThat(inventory.getSkuCode()).isEqualTo("TS-BASIC-001-BLK-L");
        assertThat(inventory.getAvailableStock()).isEqualTo(8);
        assertThat(inventory.getInStock()).isTrue();
    }
}
