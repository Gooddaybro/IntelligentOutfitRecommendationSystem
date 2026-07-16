import { beforeEach, describe, expect, it } from "vitest";
import { mockApi, resetMockApi } from "./mockApi";

describe("前端演示数据接口", () => {
  beforeEach(resetMockApi);

  it("支持无需后端的登录和商品详情预览", async () => {
    expect((await mockApi.login("preview", "preview")).accessToken).toBe("mock-access-token");
    expect((await mockApi.me()).role).toBe("ADMIN");
    const products = await mockApi.recommendationCandidates({});
    expect(products.length).toBeGreaterThan(3);
    expect((await mockApi.productDetail(products[0].spuId)).name).toBe(products[0].name);
  });

  it("演示加购会产生可见状态变化", async () => {
    const [sku] = await mockApi.recommendationCandidates({});
    expect(await mockApi.cart()).toEqual([]);
    expect(await mockApi.addCartItem(sku.skuId, 1)).toEqual([expect.objectContaining({ skuId: sku.skuId, quantity: 1 })]);
  });
});
