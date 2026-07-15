package com.recommendation.intelligentoutfitrecommendationsystem.inventory;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryApplicationServiceTests {

    @Mock
    private InventoryMapper inventoryMapper;

    private InventoryApplicationService inventoryApplicationService;

    @BeforeEach
    void setUp() {
        inventoryApplicationService = new InventoryApplicationService(inventoryMapper);
    }

    @Test
    void lockDelegatesPositiveQuantity() {
        when(inventoryMapper.lockStock(2102L, 2)).thenReturn(1);

        inventoryApplicationService.lock(2102L, 2);

        verify(inventoryMapper).lockStock(2102L, 2);
    }

    @Test
    void lockRejectsInsufficientStock() {
        when(inventoryMapper.lockStock(2102L, 2)).thenReturn(0);

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> inventoryApplicationService.lock(2102L, 2));

        assertEquals("insufficient stock for sku: 2102", error.getMessage());
    }

    @Test
    void confirmDelegatesLockedStockTransition() {
        when(inventoryMapper.confirmSoldStock(2102L, 2)).thenReturn(1);

        inventoryApplicationService.confirm(2102L, 2);

        verify(inventoryMapper).confirmSoldStock(2102L, 2);
    }

    @Test
    void releaseRejectsInconsistentLockedStock() {
        when(inventoryMapper.releaseLockedStock(2102L, 2)).thenReturn(0);

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> inventoryApplicationService.release(2102L, 2));

        assertEquals("locked stock is inconsistent for sku: 2102", error.getMessage());
    }

    @Test
    void rejectsNonPositiveArgumentsBeforeMapperCall() {
        assertThrows(
                BadRequestException.class,
                () -> inventoryApplicationService.lock(null, 1));
        assertThrows(
                BadRequestException.class,
                () -> inventoryApplicationService.confirm(2102L, 0));

        verify(inventoryMapper, never()).lockStock(null, 1);
        verify(inventoryMapper, never()).confirmSoldStock(2102L, 0);
    }
}
