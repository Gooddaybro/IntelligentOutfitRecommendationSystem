import { expect, test, type Page } from "@playwright/test";

type ApiResult<T> = {
  status: number;
  headers: Record<string, string>;
  data: T;
  errorCode?: string;
  message?: string;
};

type Address = {
  id: number;
  recipientName: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
  isDefault: boolean;
};

type Candidate = { spuId: number; skuId: number; salePrice: number };
type Preview = { payableAmount: number; invalidReasons: string[] };
type Order = {
  orderNo: string;
  status: string;
  totalAmount: number;
  address: Address;
  items: Array<{ skuId: number; salePrice: number; quantity: number; lineAmount: number }>;
};
type Payment = { paymentNo: string; orderNo: string; amount: number; status: string };
type Tokens = { accessToken: string };

async function apiCall<T>(
  page: Page,
  path: string,
  options: { method?: string; body?: unknown; token?: string | null; headers?: Record<string, string> } = {}
): Promise<ApiResult<T>> {
  return page.evaluate(async ({ path, options }) => {
    const token = options.token === undefined ? localStorage.getItem("ior.accessToken") : options.token;
    const headers: Record<string, string> = { ...(options.headers ?? {}) };
    if (options.body !== undefined) headers["Content-Type"] = "application/json";
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(path, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
    const payload = await response.json();
    return {
      status: response.status,
      headers: Object.fromEntries(response.headers.entries()),
      data: payload.data,
      errorCode: payload.errorCode,
      message: payload.message
    };
  }, { path, options });
}

test("真实 React、Java、Python 与 MySQL 完成可信交易黄金链路", async ({ page }) => {
  const unique = `${Date.now()}${Math.floor(Math.random() * 10000)}`;
  const username = `golden_${unique}`;
  const password = "StrongPassword123!";
  const originalDetail = `黄金链路 ${unique} 号`;

  await page.goto("/");
  await page.getByTestId("auth-register-mode").click();
  await page.getByTestId("auth-username").fill(username);
  await page.getByTestId("auth-password").fill(password);
  await page.getByTestId("auth-email").fill(`${username}@example.com`);
  await page.getByTestId("auth-submit").click();
  await expect(page).toHaveURL(/\/app\/home$/, { timeout: 30_000 });

  await page.goto("/app/ai");
  await page.getByTestId("chat-filter-style").fill("commute");
  await page.getByTestId("chat-filter-budget").fill("500");
  await page.getByTestId("ai-chat-input").fill("通勤简约，预算 500 以内");
  const streamResponse = page.waitForResponse(
    (response) => response.url().includes("/api/assistant/chat/stream") && response.request().method() === "POST"
  );
  await page.getByTestId("ai-chat-submit").click();
  const streamed = await streamResponse;
  expect(streamed.status()).toBe(200);
  expect(streamed.headers()["content-type"]).toContain("text/event-stream");
  await expect(page.getByTestId("chat-message-assistant").last()).not.toHaveText("", { timeout: 60_000 });

  const card = page.getByTestId("recommendation-card").first();
  await expect(card).toBeVisible({ timeout: 30_000 });
  const skuId = Number(await card.getAttribute("data-sku-id"));
  expect(skuId).toBeGreaterThan(0);
  const javaCandidates = await apiCall<Candidate[]>(page, "/api/products/recommendation-candidates");
  expect(javaCandidates.status).toBe(200);
  expect(javaCandidates.data.some((candidate) => candidate.skuId === skuId)).toBe(true);

  await card.getByTestId("add-to-cart-action").click();
  await page.getByTestId("confirm-action-submit").click();
  await expect(page.getByLabel("购物车摘要")).toContainText("1 款");

  await page.goto("/app/profile/addresses");
  await page.getByRole("button", { name: "新增地址" }).click();
  await page.getByLabel("收货人").fill("黄金用户");
  await page.getByLabel("手机号").fill("13800138000");
  await page.getByLabel("省份").fill("浙江省");
  await page.getByLabel("城市").fill("杭州市");
  await page.getByLabel("区县").fill("西湖区");
  await page.getByLabel("详细地址").fill(originalDetail);
  await page.getByRole("button", { name: "保存地址" }).click();
  await expect(page.getByText(originalDetail)).toBeVisible();
  const addresses = await apiCall<Address[]>(page, "/api/addresses");
  const address = addresses.data.find((item) => item.detail === originalDetail);
  expect(address?.isDefault).toBe(true);

  await page.goto("/app/cart");
  const cartRow = page.getByTestId("cart-row");
  await expect(cartRow).toHaveCount(1);
  await cartRow.getByRole("checkbox").check();
  await expect(page.getByTestId("checkout-submit")).toBeEnabled();
  await page.getByTestId("checkout-submit").click();
  await expect(page.getByRole("heading", { name: "确认订单" })).toBeVisible();
  const preview = await apiCall<Preview>(page, "/api/checkout/preview", {
    method: "POST",
    body: { skuIds: [skuId], addressId: address!.id }
  });
  expect(preview.status).toBe(200);
  expect(preview.data.invalidReasons).toEqual([]);
  await page.getByRole("button", { name: "提交订单" }).click();
  await expect(page.getByRole("heading", { name: "订单已创建" })).toBeVisible();
  const orderNo = page.url().split("/").pop()!;

  await page.getByRole("button", { name: "演示支付" }).click();
  await expect(page.getByRole("heading", { name: "支付成功" })).toBeVisible();
  await page.getByRole("link", { name: "查看订单详情" }).click();
  await expect(page.getByRole("heading", { name: `订单 ${orderNo}` })).toBeVisible();

  const order = await apiCall<Order>(page, `/api/orders/${encodeURIComponent(orderNo)}`);
  expect(order.status).toBe(200);
  expect(order.data.status).toBe("PAID");
  expect(order.data.totalAmount).toBe(preview.data.payableAmount);
  expect(order.data.items[0].lineAmount).toBe(order.data.items[0].salePrice * order.data.items[0].quantity);
  expect(order.data.address.detail).toBe(originalDetail);

  const changedDetail = `已修改 ${unique} 号`;
  const updatedAddresses = await apiCall<Address[]>(page, `/api/addresses/${address!.id}`, {
    method: "PUT",
    body: { ...address, detail: changedDetail }
  });
  expect(updatedAddresses.status).toBe(200);
  const unchangedOrder = await apiCall<Order>(page, `/api/orders/${encodeURIComponent(orderNo)}`);
  expect(unchangedOrder.data.address.detail).toBe(originalDetail);

  const duplicatePaymentOne = await apiCall<Payment>(page, "/api/payments/mock-pay", {
    method: "POST",
    body: { orderNo }
  });
  const duplicatePaymentTwo = await apiCall<Payment>(page, "/api/payments/mock-pay", {
    method: "POST",
    body: { orderNo }
  });
  expect(duplicatePaymentTwo.data.paymentNo).toBe(duplicatePaymentOne.data.paymentNo);
  expect(duplicatePaymentTwo.data.status).toBe("SUCCESS");
  const payment = await apiCall<Payment>(page, `/api/payments/${duplicatePaymentOne.data.paymentNo}`);
  expect(payment.data.orderNo).toBe(orderNo);

  const secondAddressResult = await apiCall<Address[]>(page, "/api/addresses", {
    method: "POST",
    body: {
      recipientName: "备用地址",
      phone: "13900139000",
      province: "上海市",
      city: "上海市",
      district: "徐汇区",
      detail: `备用 ${unique} 号`
    }
  });
  const secondAddress = secondAddressResult.data.find((item) => item.recipientName === "备用地址")!;
  await apiCall(page, "/api/cart/items", { method: "POST", body: { skuId, quantity: 1 } });
  const idempotencyKey = crypto.randomUUID();
  const orderRequest = { source: "CART", skuIds: [skuId], addressId: address!.id };
  const firstOrder = await apiCall<Order>(page, "/api/orders", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: orderRequest
  });
  const replayedOrder = await apiCall<Order>(page, "/api/orders", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: orderRequest
  });
  expect(replayedOrder.data.orderNo).toBe(firstOrder.data.orderNo);
  expect(replayedOrder.headers["idempotency-replayed"]).toBe("true");
  const conflictingOrder = await apiCall<Order>(page, "/api/orders", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: { ...orderRequest, addressId: secondAddress.id }
  });
  expect(conflictingOrder.status).toBe(409);

  const otherUsername = `golden_other_${unique}`;
  await apiCall(page, "/api/auth/register", {
    method: "POST",
    token: null,
    body: { username: otherUsername, password, email: `${otherUsername}@example.com` }
  });
  const otherLogin = await apiCall<Tokens>(page, "/api/auth/login", {
    method: "POST",
    token: null,
    body: { username: otherUsername, password }
  });
  const foreignPreview = await apiCall<Preview>(page, "/api/checkout/preview", {
    method: "POST",
    token: otherLogin.data.accessToken,
    body: { skuIds: [skuId], addressId: address!.id }
  });
  expect(foreignPreview.status).toBe(404);
  expect(foreignPreview.message).toBe("address not found");
});
