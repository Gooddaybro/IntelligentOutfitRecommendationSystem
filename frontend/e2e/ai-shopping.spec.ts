import { expect, test } from "@playwright/test";
import { installApiMocks } from "./fixtures/api";

async function login(page: import("@playwright/test").Page) {
  await page.goto("/");
  await page.getByTestId("auth-username").fill("e2e_user");
  await page.getByTestId("auth-password").fill("StrongPassword123!");
  await page.getByTestId("auth-submit").click();
  await expect(page).toHaveURL(/\/app\/home$/);
}

test("AI 推荐可以确认加入购物袋并完成下单支付", async ({ page }) => {
  const api = await installApiMocks(page);
  await login(page);

  await page.locator(".shuimu-nav").getByRole("link", { name: "AI 造型师" }).click();
  await expect(page.getByTestId("recommendation-card").first()).toContainText("通勤轻薄外套");

  await page.getByTestId("chat-filter-style").fill("commute");
  await page.getByTestId("chat-filter-budget").fill("300");
  await page.getByTestId("ai-chat-input").fill("通勤、预算 300、想要简洁一点");
  await page.getByTestId("ai-chat-submit").click();
  await expect(page.getByTestId("chat-message-assistant").last()).toContainText("通勤建议优先看黑色轻薄外套。");

  await page.getByTestId("recommendation-card").first().getByTestId("add-to-cart-action").click();
  await expect(page.getByTestId("confirm-action-dialog")).toContainText("加入购物车");
  await page.getByTestId("confirm-action-submit").click();
  await expect(page.getByLabel("购物车摘要")).toContainText("1 款");
  expect(api.capturedBodies.addCart).toEqual([{ skuId: 2102, quantity: 1 }]);

  await page.locator(".shuimu-nav").getByRole("link", { name: /购物袋/ }).click();
  await expect(page.getByTestId("cart-row")).toContainText("通勤轻薄外套");
  await page.getByTestId("checkout-submit").click();
  await expect(page.getByTestId("order-row")).toContainText("ORD-E2E-001");
  await page.getByTestId("mock-pay-submit").click();
  await expect(page.getByTestId("order-row")).toContainText("PAID");
});

test("窄屏使用五项底部导航进入 AI 造型师", async ({ page }) => {
  await installApiMocks(page);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);

  const mobileNav = page.getByRole("navigation", { name: "移动端主导航" });
  await expect(mobileNav.getByRole("link")).toHaveCount(5);
  await mobileNav.getByRole("link", { name: "AI 造型师" }).click();
  await expect(page.getByTestId("ai-chat-input")).toBeVisible();
});

test("首页、商品探索和 AI 工作台路由可连续切换", async ({ page }) => {
  await installApiMocks(page);
  await login(page);
  const nav = page.locator(".shuimu-nav");
  await nav.getByRole("link", { name: "探索商品" }).click();
  await expect(page).toHaveURL(/\/app\/products$/);
  await nav.getByRole("link", { name: "AI 造型师" }).click();
  await expect(page.getByTestId("recommendation-card").first()).toBeVisible();
});
