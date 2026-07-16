import { describe, expect, it } from "vitest";
import type { AdminProduct } from "../../shared/api/adminTypes";
import { filterAdminProducts } from "./adminFilters";

const products: AdminProduct[] = [
  { spuId: 1, spuCode: "COAT-01", name: "浅卡其通勤风衣", categoryId: 2, categoryName: "外套", minPrice: 599, maxPrice: 599, skuCount: 2, totalStock: 12, status: "ON_SALE", createdAt: "2026-07-01" },
  { spuId: 2, spuCode: "SHIRT-01", name: "牛津纺衬衫", categoryId: 1, categoryName: "上装", minPrice: 269, maxPrice: 299, skuCount: 3, totalStock: 18, status: "OFF_SHELF", createdAt: "2026-07-02" }
];

describe("管理端商品筛选", () => {
  it("关键词、分类和状态共同限制结果", () => {
    expect(filterAdminProducts(products, { keyword: "COAT", category: "外套", status: "ON_SALE" })).toEqual([products[0]]);
    expect(filterAdminProducts(products, { keyword: "衬衫", category: "外套", status: "ALL" })).toEqual([]);
  });
});
