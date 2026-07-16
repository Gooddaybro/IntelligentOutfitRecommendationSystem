import type { CartItem } from "../../shared/api/types";

export function cartItemIssue(item: CartItem): string | undefined {
  if (item.stockStatus === "OUT_OF_STOCK" || (item.availableStock ?? 1) <= 0) return "商品暂时缺货";
  if (item.availableStock !== undefined && item.quantity > item.availableStock) return "购买数量超过可用库存";
}

export function selectedCartTotal(items: CartItem[], selectedSkuIds: number[]) {
  return items.filter((item) => selectedSkuIds.includes(item.skuId) && !cartItemIssue(item)).reduce((sum, item) => sum + item.salePrice * item.quantity, 0);
}
