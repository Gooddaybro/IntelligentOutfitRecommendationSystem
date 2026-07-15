package com.recommendation.intelligentoutfitrecommendationsystem.inventory.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import org.springframework.stereotype.Service;

/**
 * 库存写操作的模块公开入口，避免订单和支付模块直接依赖库存 Mapper。
 *
 * <p>本服务不启动独立事务；调用方的订单或支付事务负责把库存状态转换与业务状态更新保持在
 * 同一个本地事务中。</p>
 */
@Service
public class InventoryApplicationService {

    private final InventoryMapper inventoryMapper;

    public InventoryApplicationService(InventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    /**
     * 为未支付订单原子锁定可售库存。
     *
     * @param skuId SKU 标识，必须为正数
     * @param quantity 锁定数量，必须为正数
     * @throws BadRequestException 参数非法或可售库存不足时抛出
     */
    public void lock(Long skuId, Integer quantity) {
        validateArguments(skuId, quantity);
        if (inventoryMapper.lockStock(skuId, quantity) == 0) {
            throw new BadRequestException("insufficient stock for sku: " + skuId);
        }
    }

    /**
     * 支付成功后把锁定库存转换为已售库存。
     *
     * @param skuId SKU 标识，必须为正数
     * @param quantity 确认数量，必须为正数
     * @throws BadRequestException 参数非法或锁定库存状态不一致时抛出
     */
    public void confirm(Long skuId, Integer quantity) {
        validateArguments(skuId, quantity);
        requireConsistentTransition(inventoryMapper.confirmSoldStock(skuId, quantity), skuId);
    }

    /**
     * 订单取消或关闭后把锁定库存释放回可售库存。
     *
     * @param skuId SKU 标识，必须为正数
     * @param quantity 释放数量，必须为正数
     * @throws BadRequestException 参数非法或锁定库存状态不一致时抛出
     */
    public void release(Long skuId, Integer quantity) {
        validateArguments(skuId, quantity);
        requireConsistentTransition(inventoryMapper.releaseLockedStock(skuId, quantity), skuId);
    }

    private void validateArguments(Long skuId, Integer quantity) {
        if (skuId == null || skuId <= 0 || quantity == null || quantity <= 0) {
            throw new BadRequestException("skuId and quantity must be positive");
        }
    }

    private void requireConsistentTransition(int affectedRows, Long skuId) {
        if (affectedRows == 0) {
            throw new BadRequestException("locked stock is inconsistent for sku: " + skuId);
        }
    }
}
