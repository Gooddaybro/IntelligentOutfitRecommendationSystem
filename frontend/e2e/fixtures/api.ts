import type { Page, Route } from "@playwright/test";

type ApiEnvelope<T> = {
  data: T;
};

type CartItem = {
  id: number;
  userId: number;
  skuId: number;
  spuId: number;
  skuCode: string;
  spuCode: string;
  name: string;
  categoryName: string;
  color: string;
  size: string;
  salePrice: number;
  stockStatus: string;
  quantity: number;
  availableStock: number;
};

type Order = {
  orderNo: string;
  status: "UNPAID" | "PAID";
  totalAmount: number;
  items: Array<{
    skuId: number;
    spuId: number;
    skuCode: string;
    spuCode: string;
    productName: string;
    categoryName: string;
    color: string;
    size: string;
    salePrice: number;
    quantity: number;
    lineAmount: number;
  }>;
  createdAt: string;
  paidAt?: string | null;
  address?: {
    recipientName: string;
    phone: string;
    province: string;
    city: string;
    district: string;
    detail: string;
  };
};

type InstallApiMockOptions = {
  role?: "ROLE_USER" | "ROLE_ADMIN";
  assistantScenario?: "default" | "browseFallback" | "partialMatch";
};

export const commuteJacketCandidate = {
  spuId: 1002,
  skuId: 2102,
  spuCode: "JACKET_COMMUTE_001",
  name: "通勤轻薄外套",
  categoryName: "外套",
  mainImageUrl: "/images/products/jacket-commute-main.svg",
  fitType: "regular",
  color: "黑色",
  size: "L",
  materials: "聚酯纤维混纺",
  seasons: "春秋",
  styleTags: "commute,minimal",
  salePrice: 299,
  stockStatus: "in_stock",
  minPrice: 299,
  maxPrice: 299,
  totalAvailableStock: 7
};

export const summerOutfitCandidates = [
  {
    ...commuteJacketCandidate,
    spuId: 3101,
    skuId: 4101,
    spuCode: "SUMMER-TOP-001",
    name: "夏季亚麻短袖衬衫",
    categoryName: "衬衫",
    mainImageUrl: "/images/products/basic-cotton-tshirt-main.svg",
    color: "白色",
    size: "M",
    materials: "亚麻",
    seasons: "summer",
    styleTags: "casual",
    salePrice: 199,
    minPrice: 199,
    maxPrice: 199,
    totalAvailableStock: 8
  },
  {
    ...commuteJacketCandidate,
    spuId: 3102,
    skuId: 4102,
    spuCode: "SUMMER-BOTTOM-001",
    name: "夏季休闲短裤",
    categoryName: "短裤",
    mainImageUrl: "/images/products/basic-cotton-tshirt-main.svg",
    color: "卡其色",
    size: "M",
    materials: "棉",
    seasons: "summer",
    styleTags: "casual",
    salePrice: 159,
    minPrice: 159,
    maxPrice: 159,
    totalAvailableStock: 9
  }
];

function jsonResponse<T>(data: T): ApiEnvelope<T> {
  return { data };
}

async function fulfillJson<T>(route: Route, data: T) {
  await route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(jsonResponse(data))
  });
}

function buildCartItem(quantity: number): CartItem {
  return {
    id: 1,
    userId: 10001,
    skuId: commuteJacketCandidate.skuId,
    spuId: commuteJacketCandidate.spuId,
    skuCode: "JK-COMMUTE-001-BLACK-L",
    spuCode: commuteJacketCandidate.spuCode,
    name: commuteJacketCandidate.name,
    categoryName: commuteJacketCandidate.categoryName,
    color: commuteJacketCandidate.color,
    size: commuteJacketCandidate.size,
    salePrice: commuteJacketCandidate.salePrice,
    stockStatus: commuteJacketCandidate.stockStatus,
    quantity,
    availableStock: 7
  };
}

function buildOrder(orderNo: string, cartItems: CartItem[], status: "UNPAID" | "PAID" = "UNPAID"): Order {
  const items = cartItems.map((item) => ({
    skuId: item.skuId,
    spuId: item.spuId,
    skuCode: item.skuCode,
    spuCode: item.spuCode,
    productName: item.name,
    categoryName: item.categoryName,
    color: item.color,
    size: item.size,
    salePrice: item.salePrice,
    quantity: item.quantity,
    lineAmount: item.salePrice * item.quantity
  }));
  return {
    orderNo,
    status,
    totalAmount: items.reduce((sum, item) => sum + item.lineAmount, 0),
    items,
    createdAt: "2026-06-19T10:00:00",
    paidAt: status === "PAID" ? "2026-06-19T10:05:00" : null,
    address: { recipientName: "林木", phone: "13800000000", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号" }
  };
}

export async function installApiMocks(page: Page, options: InstallApiMockOptions = {}) {
  const assistantCandidates = options.assistantScenario === "default" || !options.assistantScenario
    ? [commuteJacketCandidate]
    : summerOutfitCandidates;
  let cartItems: CartItem[] = [];
  let orders: Order[] = [];
  let adminProducts = [
    { spuId: 3001, spuCode: "ADMIN-COAT-001", name: "\u901a\u52e4\u8f7b\u8584\u5916\u5957", categoryId: 2, categoryName: "\u5916\u5957", mainImageUrl: "/images/products/jacket-commute-main.svg", minPrice: 299, maxPrice: 299, skuCount: 1, totalStock: 7, status: "ON_SALE", createdAt: "2026-07-16T08:00:00Z" },
    { spuId: 3002, spuCode: "ADMIN-SHIRT-001", name: "\u57fa\u7840\u7eaf\u68c9T\u6064", categoryId: 1, categoryName: "\u4e0a\u88c5", mainImageUrl: "/images/products/basic-cotton-tshirt-main.svg", minPrice: 99, maxPrice: 99, skuCount: 1, totalStock: 12, status: "ON_SALE", createdAt: "2026-07-15T08:00:00Z" }
  ];
  let adminInventory = [
    { skuId: 4001, skuCode: "SKU-ADMIN-001", spuId: 3001, productName: "\u901a\u52e4\u8f7b\u8584\u5916\u5957", color: "\u9ed1\u8272", size: "L", salePrice: 299, availableStock: 3, lowStockThreshold: 5, status: "ACTIVE" },
    { skuId: 4002, skuCode: "SKU-ADMIN-002", spuId: 3002, productName: "\u57fa\u7840\u7eaf\u68c9T\u6064", color: "\u767d\u8272", size: "M", salePrice: 99, availableStock: 12, lowStockThreshold: 5, status: "ACTIVE" }
  ];
  let adminOrders = [
    { orderNo: "ORD-ADMIN-001", username: "admin_user", status: "PAID", paymentStatus: "PAID", totalAmount: 299, itemCount: 1, createdAt: "2026-07-16T09:00:00Z", availableActions: ["SHIP"], addressSummary: "\u6d59\u6c5f\u7701\u676d\u5dde\u5e02\u897f\u6e56\u533a" },
    { orderNo: "ORD-ADMIN-002", username: "buyer_user", status: "SHIPPED", paymentStatus: "PAID", totalAmount: 99, itemCount: 1, createdAt: "2026-07-15T09:00:00Z", availableActions: ["AFTER_SALE"], addressSummary: "\u4e0a\u6d77\u5e02\u5f90\u6c47\u533a", shipment: { carrier: "\u987a\u4e30\u901f\u8fd0", trackingNo: "SFOLD001" } }
  ];
  let adminUsers = [
    { userId: 5001, username: "admin_user", nickname: "\u6f14\u793a\u7528\u6237", email: "admin-user@example.com", phone: "13800000000", status: "ACTIVE", registeredAt: "2026-07-01T09:00:00Z", orderCount: 2, paidAmount: 398 },
    { userId: 5002, username: "buyer_user", nickname: "\u4e70\u5bb6", email: "buyer@example.com", phone: "13900000000", status: "ACTIVE", registeredAt: "2026-07-02T09:00:00Z", orderCount: 1, paidAmount: 99 }
  ];
  let adminAuditLogs = [
    { id: 1, operator: "\u8fd0\u8425\u7ba1\u7406\u5458", action: "CREATE_PRODUCT", targetType: "SPU", targetId: "3001", result: "SUCCESS", summary: "\u521d\u59cb\u5316\u6f14\u793a\u5546\u54c1", createdAt: "2026-07-16T08:00:00Z" }
  ];
  const capturedBodies: Record<string, unknown[]> = {
    addCart: [],
    createOrder: [],
    pay: [],
    behaviorEvents: []
  };

  await page.route("**/api/auth/login", async (route) => {
    await fulfillJson(route, {
      accessToken: "e2e-access-token",
      refreshToken: "e2e-refresh-token",
      tokenType: "Bearer",
      expiresIn: 3600
    });
  });

  await page.route("**/api/users/me", async (route) => {
    await fulfillJson(route, {
      userId: 10001,
      username: "e2e_user",
      role: options.role ?? "ROLE_USER"
    });
  });

  await page.route("**/api/cart/items", async (route) => {
    if (route.request().method() === "POST") {
      capturedBodies.addCart.push(route.request().postDataJSON());
      cartItems = [buildCartItem(1)];
    }

    await fulfillJson(route, cartItems);
  });

  await page.route("**/api/products?**", async (route) => {
    await fulfillJson(route, [
      {
        spuId: commuteJacketCandidate.spuId,
        spuCode: commuteJacketCandidate.spuCode,
        name: commuteJacketCandidate.name,
        categoryName: commuteJacketCandidate.categoryName,
        mainImageUrl: commuteJacketCandidate.mainImageUrl,
        fitType: commuteJacketCandidate.fitType,
        minPrice: commuteJacketCandidate.minPrice,
        maxPrice: commuteJacketCandidate.maxPrice
      }
    ]);
  });

  await page.route("**/api/products/recommendation-candidates?**", async (route) => {
    await fulfillJson(route, assistantCandidates);
  });

  await page.route(/\/api\/products\/\d+$/, async (route) => {
    await fulfillJson(route, {
      spuId: commuteJacketCandidate.spuId,
      spuCode: commuteJacketCandidate.spuCode,
      name: commuteJacketCandidate.name,
      categoryName: commuteJacketCandidate.categoryName,
      mainImageUrl: commuteJacketCandidate.mainImageUrl,
      minPrice: commuteJacketCandidate.minPrice,
      maxPrice: commuteJacketCandidate.maxPrice,
      description: "适合春秋通勤的轻薄外套。"
    });
  });

  await page.route("**/api/favorites**", async (route) => {
    await fulfillJson(route, []);
  });

  await page.route("**/api/addresses", async (route) => {
    await fulfillJson(route, [{ id: 1, recipientName: "林木", phone: "13800000000", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号", isDefault: true }]);
  });

  await page.route("**/api/checkout/preview", async (route) => {
    const merchandiseAmount = cartItems.reduce((sum, item) => sum + item.salePrice * item.quantity, 0);
    await fulfillJson(route, { items: cartItems, merchandiseAmount, shippingAmount: 0, discountAmount: 0, payableAmount: merchandiseAmount, invalidReasons: [] });
  });

  await page.route("**/api/behavior/events", async (route) => {
    const body = route.request().postDataJSON() as { eventId?: string };
    capturedBodies.behaviorEvents.push(body);
    await fulfillJson(route, { eventId: body.eventId ?? "evt-e2e" });
  });

  await page.route("**/api/assistant/chat/stream", async (route) => {
    const resolvedIntent = {
      version: "demand-intent-v3",
      requestType: "OUTFIT_ADVICE",
      requestedCapabilities: ["OUTFIT_PLAN", "PRODUCT_SELECTION"],
      hardFilters: [],
      softPreferences: []
    };
    const donePayload = options.assistantScenario === "browseFallback"
      ? {
          thread_id: "thread-e2e",
          answer: "当前先展示可浏览的夏季候选。",
          recommended_spu_ids: [],
          recommended_items: [],
          java_candidate_count: 2,
          recommendation_status: "BROWSE_FALLBACK",
          resolved_intent: resolvedIntent
        }
      : options.assistantScenario === "partialMatch"
        ? {
            thread_id: "thread-e2e",
            answer: "已找到真实上装，下装请参考文字建议。",
            recommended_spu_ids: [3101],
            recommended_items: [{
              spuId: 3101,
              skuId: 4101,
              reason: "夏季休闲上装匹配。",
              rankScore: 0.91,
              outfitRole: "TOP"
            }],
            java_candidate_count: 2,
            recommendation_status: "PARTIAL_MATCH",
            resolved_intent: resolvedIntent
          }
        : {
            thread_id: "thread-e2e",
            answer: "",
            recommended_spu_ids: [1002],
            recommended_items: [{
              spuId: 1002,
              skuId: 2102,
              reason: "黑色通勤外套符合预算和场景。",
              rankScore: 0.92
            }]
          };
    await route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: [
        'event: meta\ndata: {"thread_id":"thread-e2e"}',
        'event: token\ndata: {"content":"通勤建议优先看黑色轻薄外套。"}',
        'event: recommendation\ndata: {"recommended_spu_ids":[1002]}',
        `event: done\ndata: ${JSON.stringify(donePayload)}`,
        ""
      ].join("\n\n")
    });
  });

  await page.route("**/api/orders", async (route) => {
    if (route.request().method() === "POST") {
      capturedBodies.createOrder.push(route.request().postDataJSON());
      const order = buildOrder("ORD-E2E-001", cartItems);
      orders = [order];
      cartItems = [];
      await fulfillJson(route, order);
      return;
    }

    await fulfillJson(route, orders);
  });

  await page.route(/\/api\/orders\/[^/]+$/, async (route) => {
    const orderNo = decodeURIComponent(new URL(route.request().url()).pathname.split("/").pop() || "");
    const order = orders.find((item) => item.orderNo === orderNo);
    if (!order) {
      await route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ message: "订单不存在" }) });
      return;
    }
    await fulfillJson(route, order);
  });

  await page.route("**/api/payments/mock-pay", async (route) => {
    const body = route.request().postDataJSON() as { orderNo: string };
    orders = orders.map((order) => (order.orderNo === body.orderNo ? { ...order, status: "PAID", paidAt: "2026-06-19T10:05:00" } : order));
    await fulfillJson(route, { paymentNo: "PAY-E2E-001", orderNo: body.orderNo, amount: 299, channel: "MOCK", status: "SUCCESS", transactionId: "TX-E2E-001", paidAt: "2026-06-19T10:05:00" });
  });

  await page.route("**/api/payments", async (route) => {
    capturedBodies.pay.push(route.request().postDataJSON());
    const body = route.request().postDataJSON() as { orderNo: string };
    orders = orders.map((order) => (order.orderNo === body.orderNo ? { ...order, status: "PAID", paidAt: "2026-06-19T10:05:00" } : order));
    await fulfillJson(route, {
      paymentNo: "PAY-E2E-001",
      orderNo: body.orderNo,
      amount: 299,
      channel: "MOCK",
      status: "SUCCESS",
      transactionId: "TX-E2E-001",
      paidAt: "2026-06-19T10:05:00"
    });
  });

  await page.route("**/api/admin/overview", async (route) => {
    const paidOrders = adminOrders.filter((order) => order.paymentStatus === "PAID");
    await fulfillJson(route, {
      onSaleProducts: adminProducts.filter((product) => product.status === "ON_SALE").length,
      skuCount: adminInventory.length,
      lowStockCount: adminInventory.filter((sku) => sku.availableStock <= sku.lowStockThreshold).length,
      pendingShipmentOrders: adminOrders.filter((order) => order.status === "PAID" && order.availableActions.includes("SHIP")).length,
      afterSaleOrders: adminOrders.filter((order) => order.availableActions.includes("AFTER_SALE")).length,
      orderCount: adminOrders.length,
      paidAmount: paidOrders.reduce((sum, order) => sum + order.totalAmount, 0),
      rangeLabel: "\u6700\u8fd1 30 \u5929",
      trend: [{ label: "07-15", amount: 99 }, { label: "07-16", amount: 299 }],
      hotProducts: adminProducts.map((product, index) => ({ spuId: product.spuId, name: product.name, sales: 12 - index * 2 }))
    });
  });

  await page.route("**/api/admin/products", async (route) => {
    await fulfillJson(route, adminProducts);
  });

  await page.route(/\/api\/admin\/products\/\d+\/status$/, async (route) => {
    const parts = new URL(route.request().url()).pathname.split("/");
    const spuId = Number(parts[4]);
    const body = route.request().postDataJSON() as { status: string };
    adminProducts = adminProducts.map((product) => product.spuId === spuId ? { ...product, status: body.status } : product);
    const updated = adminProducts.find((product) => product.spuId === spuId);
    adminAuditLogs = [{ id: Date.now(), operator: "\u8fd0\u8425\u7ba1\u7406\u5458", action: "SET_PRODUCT_STATUS", targetType: "SPU", targetId: String(spuId), result: "SUCCESS", summary: body.status, createdAt: "2026-07-16T10:00:00Z" }, ...adminAuditLogs];
    await fulfillJson(route, updated);
  });

  await page.route("**/api/admin/inventory", async (route) => {
    await fulfillJson(route, adminInventory);
  });

  await page.route(/\/api\/admin\/inventory\/\d+\/adjustments$/, async (route) => {
    const parts = new URL(route.request().url()).pathname.split("/");
    const skuId = Number(parts[4]);
    const body = route.request().postDataJSON() as { targetStock: number; reason: string };
    const current = adminInventory.find((sku) => sku.skuId === skuId)!;
    const updated = { ...current, availableStock: body.targetStock, lastAdjustment: { beforeStock: current.availableStock, afterStock: body.targetStock, reason: body.reason, operator: "\u8fd0\u8425\u7ba1\u7406\u5458", adjustedAt: "2026-07-16T10:00:00Z" } };
    adminInventory = adminInventory.map((sku) => sku.skuId === skuId ? updated : sku);
    adminAuditLogs = [{ id: Date.now(), operator: "\u8fd0\u8425\u7ba1\u7406\u5458", action: "ADJUST_STOCK", targetType: "SKU", targetId: String(skuId), result: "SUCCESS", summary: body.reason, createdAt: "2026-07-16T10:00:00Z" }, ...adminAuditLogs];
    await fulfillJson(route, updated);
  });

  await page.route("**/api/admin/orders", async (route) => {
    await fulfillJson(route, adminOrders);
  });

  await page.route(/\/api\/admin\/orders\/[^/]+\/ship$/, async (route) => {
    const parts = new URL(route.request().url()).pathname.split("/");
    const orderNo = decodeURIComponent(parts[4]);
    const body = route.request().postDataJSON() as { carrier: string; trackingNo: string };
    const updated = { ...adminOrders.find((order) => order.orderNo === orderNo)!, status: "SHIPPED", availableActions: [], shipment: { carrier: body.carrier, trackingNo: body.trackingNo } };
    adminOrders = adminOrders.map((order) => order.orderNo === orderNo ? updated : order);
    adminAuditLogs = [{ id: Date.now(), operator: "\u8fd0\u8425\u7ba1\u7406\u5458", action: "SHIP_ORDER", targetType: "ORDER", targetId: orderNo, result: "SUCCESS", summary: body.trackingNo, createdAt: "2026-07-16T10:00:00Z" }, ...adminAuditLogs];
    await fulfillJson(route, updated);
  });

  await page.route("**/api/admin/users", async (route) => {
    await fulfillJson(route, adminUsers);
  });

  await page.route(/\/api\/admin\/users\/\d+\/status$/, async (route) => {
    const parts = new URL(route.request().url()).pathname.split("/");
    const userId = Number(parts[4]);
    const body = route.request().postDataJSON() as { status: string };
    adminUsers = adminUsers.map((user) => user.userId === userId ? { ...user, status: body.status } : user);
    const updated = adminUsers.find((user) => user.userId === userId);
    adminAuditLogs = [{ id: Date.now(), operator: "\u8fd0\u8425\u7ba1\u7406\u5458", action: body.status === "DISABLED" ? "DISABLE_USER" : "ENABLE_USER", targetType: "USER", targetId: String(userId), result: "SUCCESS", summary: body.status, createdAt: "2026-07-16T10:00:00Z" }, ...adminAuditLogs];
    await fulfillJson(route, updated);
  });

  await page.route("**/api/admin/analytics", async (route) => {
    await fulfillJson(route, {
      rangeLabel: "\u6700\u8fd1 30 \u5929",
      orderCount: adminOrders.length,
      paidAmount: adminOrders.filter((order) => order.paymentStatus === "PAID").reduce((sum, order) => sum + order.totalAmount, 0),
      funnel: { exposed: 1000, clicked: 320, cartAdded: 88, purchased: 26, definition: "\u66dd\u5149\u5230\u6210\u4ea4\u7684\u6f14\u793a\u53e3\u5f84" },
      trend: [{ label: "07-15", orderCount: 1, paidAmount: 99 }, { label: "07-16", orderCount: 1, paidAmount: 299 }],
      hotProducts: adminProducts.map((product, index) => ({ spuId: product.spuId, name: product.name, sales: 18 - index, paidAmount: product.minPrice * (18 - index) })),
      categoryTrend: [{ categoryName: "\u5916\u5957", sales: 18 }, { categoryName: "\u4e0a\u88c5", sales: 12 }]
    });
  });

  await page.route("**/api/admin/audit-logs", async (route) => {
    await fulfillJson(route, adminAuditLogs);
  });

  return {
    capturedBodies,
    getCartItems: () => cartItems,
    getOrders: () => orders
  };
}
