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

    @Test
    void lockStockMovesAvailableStockToLockedStockWhenEnoughStockExists() {
        assertThat(mapper.lockStock(2102L, 2)).isEqualTo(1);

        var inventory = mapper.findBySkuId(2102L);

        assertThat(inventory.getAvailableStock()).isEqualTo(5);
        assertThat(inventory.getLockedStock()).isEqualTo(2);
    }

    @Test
    void lockStockReturnsZeroWhenAvailableStockIsInsufficient() {
        assertThat(mapper.lockStock(2004L, 1)).isZero();
    }

    @Test
    void confirmSoldStockMovesLockedStockToSoldStock() {
        assertThat(mapper.lockStock(2103L, 2)).isEqualTo(1);

        assertThat(mapper.confirmSoldStock(2103L, 2)).isEqualTo(1);

        var inventory = mapper.findBySkuId(2103L);
        assertThat(inventory.getAvailableStock()).isEqualTo(3);
        assertThat(inventory.getLockedStock()).isZero();
        assertThat(inventory.getSoldStock()).isEqualTo(2);
    }

    @Test
    void releaseLockedStockMovesLockedStockBackToAvailableStock() {
        assertThat(mapper.lockStock(2203L, 2)).isEqualTo(1);

        assertThat(mapper.releaseLockedStock(2203L, 2)).isEqualTo(1);

        var inventory = mapper.findBySkuId(2203L);
        assertThat(inventory.getAvailableStock()).isEqualTo(4);
        assertThat(inventory.getLockedStock()).isZero();
        assertThat(inventory.getSoldStock()).isZero();
    }
}
