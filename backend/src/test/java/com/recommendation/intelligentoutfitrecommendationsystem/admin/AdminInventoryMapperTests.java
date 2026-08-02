package com.recommendation.intelligentoutfitrecommendationsystem.admin;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminInventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminInventoryRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class AdminInventoryMapperTests {
    @Autowired
    private AdminInventoryMapper mapper;

    @Test
    @Transactional
    void readsLatestAdjustmentAndUpdatesAvailableStock() {
        Integer before = mapper.findAvailableStockBySkuId(2001L);
        mapper.insertInventoryAdjustment(2001L, before, 7, "mapper test", "admin");
        assertThat(mapper.updateAvailableStock(2001L, 7)).isEqualTo(1);

        AdminInventoryRow row = mapper.findSkuById(2001L);
        assertThat(row.availableStock()).isEqualTo(7);
        assertThat(row.adjustmentReason()).isEqualTo("mapper test");
    }

    @Test
    @Transactional
    void findInventoryMapsLatestAdjustmentByHighestAdjustmentId() {
        Integer before = mapper.findAvailableStockBySkuId(2001L);
        mapper.insertInventoryAdjustment(2001L, before, 5, "older mapper list test", "older-admin");
        mapper.insertInventoryAdjustment(2001L, before, 9, "latest mapper list test", "latest-admin");

        assertThat(mapper.findInventory())
                .filteredOn(row -> row.skuId().equals(2001L))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.adjustmentReason()).isEqualTo("latest mapper list test");
                    assertThat(row.adjustmentOperator()).isEqualTo("latest-admin");
                    assertThat(row.beforeStock()).isEqualTo(before);
                    assertThat(row.afterStock()).isEqualTo(9);
                });
    }
}
