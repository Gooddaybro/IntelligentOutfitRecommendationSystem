package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

/**
 * Java 已确认可展示并可发起交易操作的对话提及商品。
 *
 * <p>该 DTO 只证明 Python 返回的 SPU/SKU 精确存在于本轮 Java 可售候选池中；
 * 它不代表商品已经通过推荐证据核验，也不能用于生成“AI 强推荐”归因；库存、价格和商品状态
 * 仍由购物车或下单接口在交易边界内重新校验。</p>
 *
 * @param spuId Java 商品 SPU ID
 * @param skuId Java 可售 SKU ID
 * @param outfitRole Java 根据商品分类生成的穿搭角色
 */
public record AssistantMentionedItem(
        Long spuId,
        Long skuId,
        String outfitRole
) {
}
