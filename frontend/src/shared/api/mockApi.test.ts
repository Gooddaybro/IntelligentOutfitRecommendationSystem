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

  it("根据地址和购物袋生成结算预览并更新订单支付状态", async () => {
    const [sku] = await mockApi.recommendationCandidates({});
    await mockApi.addCartItem(sku.skuId, 2);
    const [address] = await mockApi.addresses();
    const preview = await mockApi.checkoutPreview([sku.skuId], address.id);
    expect(preview.payableAmount).toBe(sku.salePrice * 2);
    const order = await mockApi.createOrder([sku.skuId], address.id);
    expect(order.address?.id).toBe(address.id);
    await mockApi.payMock(order.orderNo);
    expect((await mockApi.order(order.orderNo)).status).toBe("PAID");
  });

  it("管理端概览、商品状态和库存调整共享同一份演示事实", async () => {
    const overview = await mockApi.adminOverview();
    const products = await mockApi.adminProducts();
    const inventory = await mockApi.adminInventory();
    expect(overview.onSaleProducts).toBe(products.filter((item) => item.status === "ON_SALE").length);
    expect(overview.skuCount).toBe(inventory.length);

    await mockApi.adminSetProductStatus(products[0].spuId, "OFF_SHELF");
    expect((await mockApi.adminProducts()).find((item) => item.spuId === products[0].spuId)?.status).toBe("OFF_SHELF");

    const sku = inventory[0];
    const adjusted = await mockApi.adminAdjustInventory(sku.skuId, sku.availableStock + 5, "到货入库");
    expect(adjusted.availableStock).toBe(sku.availableStock + 5);
    expect(adjusted.lastAdjustment?.reason).toBe("到货入库");
  });

  it("supports shipping an admin demo order", async () => {
    const orders = await mockApi.adminOrders();
    const pendingShipment = orders.find((item) => item.status === "PAID" && item.availableActions.includes("SHIP"));
    expect(pendingShipment).toBeDefined();

    const carrier = "\u987a\u4e30\u901f\u8fd0";
    const shipped = await mockApi.adminShipOrder(pendingShipment!.orderNo, carrier, "SF123456789");

    expect(shipped.status).toBe("SHIPPED");
    expect(shipped.availableActions).not.toContain("SHIP");
    expect(shipped.shipment).toEqual({ carrier, trackingNo: "SF123456789" });
    expect((await mockApi.adminOrders()).find((item) => item.orderNo === pendingShipment!.orderNo)?.status).toBe("SHIPPED");
  });

  it("supports changing an admin demo user status", async () => {
    const users = await mockApi.adminUsers();
    const activeUser = users.find((item) => item.status === "ACTIVE");
    expect(activeUser).toBeDefined();

    const disabled = await mockApi.adminSetUserStatus(activeUser!.userId, "DISABLED");

    expect(disabled.status).toBe("DISABLED");
    expect((await mockApi.adminUsers()).find((item) => item.userId === activeUser!.userId)?.status).toBe("DISABLED");
  });
});
