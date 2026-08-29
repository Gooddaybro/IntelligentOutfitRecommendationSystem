package com.recommendation.intelligentoutfitrecommendationsystem.checkout.service;

import com.recommendation.intelligentoutfitrecommendationsystem.address.service.AddressService;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.mapper.CheckoutMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutCalculatedItem;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutCalculation;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutFactRow;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutInvalidReason;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 可信结算规则的唯一入口。
 *
 * 模块隐藏购物车事实读取、商品有效性、库存判断和金额公式；预览与正式下单准备复用同一流程，
 * 区别只在于预览返回无效原因，正式入口遇到相同原因立即阻断。
 */
@Service
public class CheckoutCalculator {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final CheckoutMapper checkoutMapper;

    private final AddressService addressService;

    public CheckoutCalculator(CheckoutMapper checkoutMapper, AddressService addressService) {
        this.checkoutMapper = checkoutMapper;
        this.addressService = addressService;
    }

    /**
     * 计算当前购物车选择的时点预览，不锁定价格或库存。
     */
    @Transactional(readOnly = true)
    public CheckoutCalculation previewCart(Long userId, List<Long> skuIds, Long addressId) {
        return calculate(userId, skuIds, addressId, false);
    }

    /**
     * 为正式下单重新计算；任何商品或库存无效原因都会阻止订单继续创建。
     *
     * @throws BadRequestException 商品失效或库存不足时抛出
     */
    @Transactional
    public CheckoutCalculation calculateForOrder(Long userId, List<Long> skuIds, Long addressId) {
        CheckoutCalculation calculation = calculate(userId, skuIds, addressId, true);
        if (!calculation.invalidReasons().isEmpty()) {
            CheckoutInvalidReason reason = calculation.invalidReasons().getFirst();
            throw new BadRequestException(
                    "checkout is invalid: " + reason.code() + " for sku: " + reason.skuId());
        }
        return calculation;
    }

    /**
     * 先校验地址归属，再读取当前用户购物车与最新商品事实，并只用这些服务端事实计价。
     * 正式下单用当前读取同时取事实并锁行，预览保持一致性无锁读取；共享流程保留业务无效原因，
     * 由两个公开入口决定“解释”还是“阻断”。
     */
    private CheckoutCalculation calculate(Long userId, List<Long> skuIds, Long addressId, boolean lockCartItems) {
        List<Long> normalizedSkuIds = normalizeArguments(userId, skuIds, addressId);
        addressService.requireOwnedAddress(userId, addressId);
        List<CheckoutFactRow> facts = lockCartItems
                ? checkoutMapper.findCartFactsForUpdate(userId, normalizedSkuIds)
                : checkoutMapper.findCartFacts(userId, normalizedSkuIds);
        if (facts.size() != normalizedSkuIds.size()) {
            throw new ResourceNotFoundException("cart item not found");
        }

        List<CheckoutCalculatedItem> items = new ArrayList<>();
        List<CheckoutInvalidReason> invalidReasons = new ArrayList<>();
        BigDecimal merchandiseAmount = ZERO_MONEY;
        for (CheckoutFactRow fact : facts) {
            validateFact(fact);
            BigDecimal salePrice = money(fact.getSalePrice());
            BigDecimal lineAmount = money(salePrice.multiply(BigDecimal.valueOf(fact.getQuantity())));
            merchandiseAmount = money(merchandiseAmount.add(lineAmount));
            collectInvalidReasons(fact, invalidReasons);
            items.add(toCalculatedItem(fact, salePrice, lineAmount));
        }

        return new CheckoutCalculation(
                items,
                merchandiseAmount,
                ZERO_MONEY,
                ZERO_MONEY,
                merchandiseAmount,
                invalidReasons
        );
    }

    /**
     * 校验调用方标识并按首次出现顺序去重 SKU；重复选择不会造成重复计价。
     *
     * @return 已校验、保持选择顺序且不可变的 SKU 列表
     * @throws BadRequestException 任一标识为空、非正数，或商品选择为空时抛出
     */
    private List<Long> normalizeArguments(Long userId, List<Long> skuIds, Long addressId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
        if (addressId == null || addressId <= 0) {
            throw new BadRequestException("addressId must be positive");
        }
        if (skuIds == null || skuIds.isEmpty()) {
            throw new BadRequestException("skuIds must not be empty");
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long skuId : skuIds) {
            if (skuId == null || skuId <= 0) {
                throw new BadRequestException("skuIds must be positive");
            }
            normalized.add(skuId);
        }
        return List.copyOf(normalized);
    }

    private void validateFact(CheckoutFactRow fact) {
        if (fact.getSkuId() == null || fact.getSalePrice() == null || fact.getSalePrice().signum() < 0) {
            throw new BadRequestException("checkout product facts are invalid");
        }
        if (fact.getQuantity() == null || fact.getQuantity() <= 0) {
            throw new BadRequestException("cart quantity must be positive for sku: " + fact.getSkuId());
        }
    }

    private void collectInvalidReasons(CheckoutFactRow fact, List<CheckoutInvalidReason> reasons) {
        if (!"on_sale".equals(fact.getSkuStatus()) || !"on_sale".equals(fact.getSpuStatus())) {
            reasons.add(new CheckoutInvalidReason(
                    "SKU_UNAVAILABLE",
                    fact.getSkuId(),
                    "商品已失效: " + fact.getProductName()
            ));
        }
        if (fact.getAvailableStock() == null || fact.getAvailableStock() < fact.getQuantity()) {
            reasons.add(new CheckoutInvalidReason(
                    "INSUFFICIENT_STOCK",
                    fact.getSkuId(),
                    "库存不足: " + fact.getProductName()
            ));
        }
    }

    private CheckoutCalculatedItem toCalculatedItem(
            CheckoutFactRow fact,
            BigDecimal salePrice,
            BigDecimal lineAmount
    ) {
        return new CheckoutCalculatedItem(
                fact.getSkuId(),
                fact.getSpuId(),
                fact.getSkuCode(),
                fact.getSpuCode(),
                fact.getProductName(),
                fact.getCategoryName(),
                fact.getColor(),
                fact.getSize(),
                salePrice,
                fact.getQuantity(),
                lineAmount,
                fact.getMainImageUrl()
        );
    }

    /**
     * 在结算领域边界把金额统一为两位小数，并采用电商金额常用的 HALF_UP 舍入。
     */
    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
