import { describe, expect, it } from "vitest";
import { ADMIN_NAV_ITEMS, APP_NAV_ITEMS, APP_PATHS } from "./navigation";

describe("商城路由声明", () => {
  it("为商品详情提供可深链地址", () => {
    expect(APP_PATHS.productDetail(1001)).toBe("/app/products/1001");
  });

  it("分离用户端与管理端导航", () => {
    expect(APP_NAV_ITEMS.every((item) => item.to.startsWith("/app/"))).toBe(true);
    expect(ADMIN_NAV_ITEMS.every((item) => item.to.startsWith("/admin"))).toBe(true);
  });
});
