import { describe, expect, it } from "vitest";
import type { RecommendationCandidate } from "../../shared/api/types";
import { missingSkuOptions, resolveSku } from "./skuSelection";

const skus: RecommendationCandidate[] = [
  { spuId: 1, skuId: 101, spuCode: "COAT", name: "风衣", categoryName: "外套", color: "米白", size: "S", salePrice: 599 },
  { spuId: 1, skuId: 102, spuCode: "COAT", name: "风衣", categoryName: "外套", color: "米白", size: "M", salePrice: 599 },
  { spuId: 1, skuId: 103, spuCode: "COAT", name: "风衣", categoryName: "外套", color: "棕色", size: "M", salePrice: 629 }
];

describe("SKU 组合解析", () => {
  it("只选择颜色和尺码同时匹配的 SKU", () => {
    expect(resolveSku(skus, { color: "米白", size: "M" })?.skuId).toBe(102);
  });

  it("不可用组合不返回 SKU", () => {
    expect(resolveSku(skus, { color: "棕色", size: "XL" })).toBeUndefined();
  });

  it("报告尚未选择的规格", () => {
    expect(missingSkuOptions({ color: "米白" })).toEqual(["size"]);
  });
});
