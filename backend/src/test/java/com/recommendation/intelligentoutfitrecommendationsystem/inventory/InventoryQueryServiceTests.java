package com.recommendation.intelligentoutfitrecommendationsystem.inventory;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryQueryServiceTests {

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryQueryService service;

    @Test
    void getInventoryBySkuIdReturnsMapperResult() {
        when(inventoryMapper.findBySkuId(2003L))
                .thenReturn(new InventoryView(2003L, "TS-BASIC-001-BLK-L", 1001L, "基础款纯棉T恤", "黑色", "L", 8, 0, 0, true));

        var inventory = service.getInventoryBySkuId(2003L);

        assertThat(inventory.getAvailableStock()).isEqualTo(8);
        assertThat(inventory.getInStock()).isTrue();
    }

    @Test
    void getInventoryBySkuIdRejectsNonPositiveSkuId() {
        assertThatThrownBy(() -> service.getInventoryBySkuId(0L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("skuId must be positive");
    }
}
