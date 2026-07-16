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

export async function installApiMocks(page: Page) {
  let cartItems: CartItem[] = [];
  let orders: Order[] = [];
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
      role: "ROLE_USER"
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
    await fulfillJson(route, [commuteJacketCandidate]);
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
    await route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: [
        'event: meta\ndata: {"thread_id":"thread-e2e"}',
        'event: token\ndata: {"content":"通勤建议优先看黑色轻薄外套。"}',
        'event: recommendation\ndata: {"recommended_spu_ids":[1002]}',
        'event: done\ndata: {"thread_id":"thread-e2e","answer":"","recommended_spu_ids":[1002],"recommended_items":[{"spuId":1002,"skuId":2102,"reason":"黑色通勤外套符合预算和场景。","rankScore":0.92}]}',
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

  return {
    capturedBodies,
    getCartItems: () => cartItems,
    getOrders: () => orders
  };
}
