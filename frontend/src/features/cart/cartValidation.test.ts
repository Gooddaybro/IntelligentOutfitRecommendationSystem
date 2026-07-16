import { describe, expect, it } from "vitest";
import type { CartItem } from "../../shared/api/types";
import { cartItemIssue, selectedCartTotal } from "./cartValidation";

const item = { skuId: 1, quantity: 2, salePrice: 99, availableStock: 3, stockStatus: "IN_STOCK" } as CartItem;

describe("cartValidation", () => {
  it("识别缺货和超出库存的购物袋商品", () => {
    expect(cartItemIssue({ ...item, availableStock: 0 })).toBe("商品暂时缺货");
    expect(cartItemIssue({ ...item, quantity: 4 })).toBe("购买数量超过可用库存");
  });

  it("只汇总有效且已选中的商品", () => {
    expect(selectedCartTotal([item, { ...item, skuId: 2, availableStock: 0 }], [1, 2])).toBe(198);
  });
});
