package com.recommendation.intelligentoutfitrecommendationsystem.admin;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAuditLogResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminInventoryAdjustmentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminAuditMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminInventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminAuditEntry;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminInventoryRow;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminInventoryService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminInventoryServiceTests {
    @Test
    void stopsBeforeAdjustmentAndAuditWhenStockUpdateAffectsNoRows() {
        InconsistentInventoryMapper inventoryMapper = new InconsistentInventoryMapper();
        CapturingAuditMapper auditMapper = new CapturingAuditMapper();
        AdminInventoryService service = new AdminInventoryService(inventoryMapper, auditMapper);

        assertThatThrownBy(() -> service.adjustInventory(
                2001L, new AdminInventoryAdjustmentRequest(8, "concurrent delete")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("sku not found");

        assertThat(inventoryMapper.adjustmentInsertCount).isZero();
        assertThat(auditMapper.auditInsertCount).isZero();
    }

    private static class InconsistentInventoryMapper implements AdminInventoryMapper {
        private int adjustmentInsertCount;

        @Override
        public List<AdminInventoryRow> findInventory() {
            return List.of();
        }

        @Override
        public AdminInventoryRow findSkuById(Long skuId) {
            return new AdminInventoryRow(
                    skuId,
                    "SKU-CONCURRENT",
                    1001L,
                    "Concurrent Product",
                    "black",
                    "M",
                    BigDecimal.TEN,
                    8,
                    "ACTIVE",
                    6,
                    8,
                    "concurrent delete",
                    "admin",
                    java.time.LocalDateTime.now()
            );
        }

        @Override
        public Integer findAvailableStockBySkuId(Long skuId) {
            return 6;
        }

        @Override
        public int updateAvailableStock(Long skuId, int targetStock) {
            return 0;
        }

        @Override
        public int insertInventoryAdjustment(
                Long skuId, int beforeStock, int afterStock, String reason, String operator) {
            adjustmentInsertCount++;
            return 1;
        }
    }

    private static class CapturingAuditMapper implements AdminAuditMapper {
        private int auditInsertCount;

        @Override
        public int insertAuditLog(AdminAuditEntry entry) {
            auditInsertCount++;
            return 1;
        }

        @Override
        public List<AdminAuditLogResponse> findAuditLogs(int limit) {
            return List.of();
        }
    }
}
