import { expect, test } from "@playwright/test";
import { installApiMocks } from "./fixtures/api";

test("AI shopping flow requires confirmation before cart and payment actions", async ({ page }) => {
  const api = await installApiMocks(page);

  await page.goto("/");
  await page.getByTestId("auth-username").fill("e2e_user");
  await page.getByTestId("auth-password").fill("StrongPassword123!");
  await page.getByTestId("auth-submit").click();

  await expect(page.getByTestId("nav-ai")).toBeVisible();
  await expect(page.getByTestId("app-shell")).toHaveClass(/is-entered/);
  await expect(page.getByTestId("app-topbar")).toContainText("NOIR.AI");
  await expect(page.getByTestId("recommendation-card").first()).toContainText("通勤轻薄外套");
  await expect(page.getByTestId("ai-workbench")).toHaveAttribute("data-layout", "editorial-stage");
  await expect(page.getByTestId("recommendation-card").first()).toHaveAttribute("data-variant", "featured");
  await expect(page.getByTestId("recommendation-card").first()).toContainText("AI 首选");
  await expect
    .poll(() => page.getByTestId("recommendation-card").first().locator("img").evaluate((image) => image.naturalWidth))
    .toBeGreaterThan(0);

  await page.getByTestId("chat-filter-style").fill("commute");
  await page.getByTestId("chat-filter-budget").fill("300");
  await page.getByTestId("ai-chat-input").fill("通勤、预算 300、想要简洁一点");
  await page.getByTestId("ai-chat-submit").click();

  await expect(page.getByTestId("chat-message-assistant").last()).toContainText("通勤建议优先看黑色轻薄外套。");
  await expect(page.getByTestId("recommendation-card").first()).toContainText("￥299");
  await expect(page.getByTestId("recommendation-card").first().getByTestId("recommendation-reason")).toContainText(
    "黑色通勤外套符合预算和场景。"
  );
  await expect
    .poll(() =>
      api.capturedBodies.behaviorEvents.some(
        (event) => (event as { eventType?: string }).eventType === "RECOMMENDATION_EXPOSED"
      )
    )
    .toBe(true);

  await page.getByTestId("recommendation-card").first().getByTestId("add-to-cart-action").click();
  await expect(page.getByTestId("confirm-action-dialog")).toContainText("加入购物车");
  await expect(page.getByTestId("confirm-action-dialog")).toHaveClass(/confirm-dialog--noir/);
  await page.getByTestId("confirm-action-cancel").click();

  await expect(page.getByTestId("confirm-action-dialog")).toHaveCount(0);
  await expect(page.getByTestId("cart-count")).toContainText("购物车 0");
  expect(api.capturedBodies.addCart).toHaveLength(0);

  await page.getByTestId("recommendation-card").first().getByTestId("add-to-cart-action").click();
  await page.getByTestId("confirm-action-submit").click();

  await expect(page.getByTestId("status-line")).toContainText("已加入购物车");
  await expect(page.getByTestId("cart-count")).toContainText("购物车 1");
  expect(api.capturedBodies.addCart).toEqual([{ skuId: 2102, quantity: 1 }]);
  await expect
    .poll(() =>
      api.capturedBodies.behaviorEvents.some(
        (event) => (event as { eventType?: string }).eventType === "RECOMMENDATION_CART_ADD"
      )
    )
    .toBe(true);

  await page.getByTestId("nav-cart").click();
  await expect(page.getByTestId("cart-row")).toContainText("通勤轻薄外套");
  await expect(page.getByTestId("cart-page")).toHaveClass(/noir-page/);
  await page.getByTestId("checkout-submit").click();

  expect(api.capturedBodies.createOrder).toEqual([{ source: "CART", skuIds: [2102] }]);
  await expect(page.getByTestId("order-row")).toContainText("ORD-E2E-001");
  await expect(page.getByTestId("order-row")).toContainText("UNPAID");
  await expect(page.getByTestId("orders-page")).toHaveClass(/noir-page/);

  await page.getByTestId("mock-pay-submit").click();

  expect(api.capturedBodies.pay).toEqual([{ orderNo: "ORD-E2E-001", channel: "MOCK" }]);
  await expect(page.getByTestId("order-row")).toContainText("PAID");
});

test("NOIR workbench stays usable with reduced motion on a narrow screen", async ({ page }) => {
  await installApiMocks(page);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.setViewportSize({ width: 390, height: 844 });

  await page.goto("/");
  await page.getByTestId("auth-username").fill("e2e_user");
  await page.getByTestId("auth-password").fill("StrongPassword123!");
  await page.getByTestId("auth-submit").click();

  await expect(page.getByTestId("app-topbar")).toHaveCSS("animation-duration", "1e-05s");
  await expect(page.getByTestId("ai-chat-input")).toBeVisible();
  await expect(page.getByTestId("recommendation-card").first()).toBeVisible();
});
