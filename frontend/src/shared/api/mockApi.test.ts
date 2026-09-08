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

  it("拒绝收藏不存在的商品", async () => {
    await expect(mockApi.addFavorite(999999)).rejects.toThrow("商品不存在");
  });

  it("按最新收藏顺序返回去重商品", async () => {
    await mockApi.addFavorite(1003);
    await mockApi.addFavorite(1001);

    expect((await mockApi.favorites()).map((item) => item.spuId)).toEqual([1001, 1003, 1002]);
  });

  it("收藏投影显式包含当前可购买性和库存", async () => {
    const [favorite] = await mockApi.favorites();

    expect(favorite).toEqual(expect.objectContaining({
      availabilityStatus: "available",
      totalAvailableStock: expect.any(Number)
    }));
  });

  it("收藏投影跟随管理端的上下架和库存事实", async () => {
    const [initialFavorite] = await mockApi.favorites();
    const sku = (await mockApi.adminInventory()).find((item) => item.spuId === initialFavorite.spuId)!;

    await mockApi.adminSetProductStatus(initialFavorite.spuId, "OFF_SHELF");
    expect(await mockApi.favorites()).toEqual([expect.objectContaining({
      spuId: initialFavorite.spuId,
      salePrice: initialFavorite.salePrice,
      availabilityStatus: "unavailable",
      totalAvailableStock: 0
    })]);

    await mockApi.adminSetProductStatus(initialFavorite.spuId, "ON_SALE");
    await mockApi.adminAdjustInventory(sku.skuId, 0, "售罄");
    expect(await mockApi.favorites()).toEqual([expect.objectContaining({
      availabilityStatus: "unavailable",
      totalAvailableStock: 0
    })]);

    await mockApi.adminAdjustInventory(sku.skuId, sku.availableStock, "补货");
    expect(await mockApi.favorites()).toEqual([expect.objectContaining({
      availabilityStatus: "available",
      totalAvailableStock: sku.availableStock
    })]);
  });

  it("根据地址和购物袋生成结算预览并更新订单支付状态", async () => {
    const [sku] = await mockApi.recommendationCandidates({});
    await mockApi.addCartItem(sku.skuId, 2);
    const [address] = await mockApi.addresses();
    const preview = await mockApi.checkoutPreview([sku.skuId], address.id);
    expect(preview.payableAmount).toBe(sku.salePrice * 2);
    const order = await mockApi.createOrder([sku.skuId], address.id, crypto.randomUUID());
    expect(order.address?.id).toBe(address.id);
    await mockApi.payMock(order.orderNo);
    expect((await mockApi.order(order.orderNo)).status).toBe("PAID");
  });

  it("创建首个地址时自动设为默认地址", async () => {
    const [initial] = await mockApi.addresses();
    await mockApi.deleteAddress(initial.id);

    const addresses = await mockApi.createAddress({
      recipientName: "新用户",
      phone: "13900000000",
      province: "上海市",
      city: "上海市",
      district: "徐汇区",
      detail: "漕溪北路 1 号"
    });

    expect(addresses).toEqual([expect.objectContaining({ recipientName: "新用户", isDefault: true })]);
  });

  it("设为默认地址时保持唯一默认地址", async () => {
    const [, second] = await mockApi.createAddress({
      recipientName: "小林",
      phone: "13900000000",
      province: "上海市",
      city: "上海市",
      district: "徐汇区",
      detail: "漕溪北路 1 号"
    });

    const addresses = await mockApi.setDefaultAddress(second.id);

    expect(addresses.filter((item) => item.isDefault)).toEqual([expect.objectContaining({ id: second.id })]);
  });

  it("删除默认地址时提升剩余地址", async () => {
    const [initial, second] = await mockApi.createAddress({
      recipientName: "小林",
      phone: "13900000000",
      province: "上海市",
      city: "上海市",
      district: "徐汇区",
      detail: "漕溪北路 1 号"
    });
    await mockApi.setDefaultAddress(second.id);

    const addresses = await mockApi.deleteAddress(second.id);

    expect(addresses).toEqual([expect.objectContaining({ id: initial.id, isDefault: true })]);
  });

  it("按默认优先及最近创建或更新顺序维护地址列表", async () => {
    const [first] = await mockApi.addresses();
    const [, second] = await mockApi.createAddress({ recipientName: "小林", phone: "13900000000", province: "上海市", city: "上海市", district: "徐汇区", detail: "漕溪北路 1 号" });
    const afterThirdCreate = await mockApi.createAddress({ recipientName: "小周", phone: "13700000000", province: "北京市", city: "北京市", district: "朝阳区", detail: "建国路 2 号" });
    const third = afterThirdCreate.find((item) => item.recipientName === "小周")!;
    expect(afterThirdCreate.map((item) => item.id)).toEqual([first.id, third.id, second.id]);

    const updated = await mockApi.updateAddress(second.id, { recipientName: "小林", phone: "13900000000", province: "上海市", city: "上海市", district: "徐汇区", detail: "漕溪北路 9 号" });
    expect(updated.map((item) => item.id)).toEqual([first.id, second.id, third.id]);

    const defaulted = await mockApi.setDefaultAddress(third.id);
    expect(defaulted.map((item) => [item.id, item.isDefault])).toEqual([[third.id, true], [second.id, false], [first.id, false]]);

    const firstDefaulted = await mockApi.setDefaultAddress(first.id);
    expect(firstDefaulted.map((item) => [item.id, item.isDefault])).toEqual([[first.id, true], [third.id, false], [second.id, false]]);

    const afterDelete = await mockApi.deleteAddress(first.id);
    expect(afterDelete.map((item) => [item.id, item.isDefault])).toEqual([[third.id, true], [second.id, false]]);
  });

  it("删除不存在的地址时与后端一致地报错", async () => {
    await expect(mockApi.deleteAddress(999)).rejects.toThrow("地址不存在");
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
