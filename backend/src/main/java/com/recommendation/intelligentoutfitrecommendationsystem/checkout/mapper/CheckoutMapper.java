package com.recommendation.intelligentoutfitrecommendationsystem.checkout.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutFactRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 结算模块的交易事实读取边界。
 *
 * 查询把用户购物车数量与当前商品、价格和库存合并，避免 Controller 或订单模块各自拼装事实。
 */
@Mapper
public interface CheckoutMapper {

    /**
     * 读取当前用户选中购物车行的最新交易事实。
     *
     * @param userId 当前认证用户 ID
     * @param skuIds 已校验、去重后的购物车 SKU 集合
     * @return 实际存在且属于当前用户的事实行，缺失选择由 Calculator 统一转换为错误
     */
    List<CheckoutFactRow> findCartFacts(
            @Param("userId") Long userId,
            @Param("skuIds") List<Long> skuIds
    );

    /**
     * 用当前读取返回并锁定正式下单所需的购物车事实。
     *
     * 单条 `FOR UPDATE` 查询避免 MySQL 可重复读事务在先前普通读取建立快照后，
     * 锁行却又通过旧快照计价。
     *
     * @param userId 当前认证用户 ID
     * @param skuIds 已校验、去重后的购物车 SKU 集合
     * @return 当前已锁定且实际存在的事实行，缺失选择由 Calculator 统一转换为错误
     */
    List<CheckoutFactRow> findCartFactsForUpdate(
            @Param("userId") Long userId,
            @Param("skuIds") List<Long> skuIds
    );
}
