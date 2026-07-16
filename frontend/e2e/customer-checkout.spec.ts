import { expect, test } from "@playwright/test";
import { installApiMocks } from "./fixtures/api";

async function login(page: import("@playwright/test").Page) {
  await page.goto("/");
  await page.getByTestId("auth-username").fill("e2e_user");
  await page.getByTestId("auth-password").fill("StrongPassword123!");
  await page.getByTestId("auth-submit").click();
  await expect(page).toHaveURL(/\/app\/home$/);
}

test("商品详情到订单详情形成完整购买链路", async ({ page }) => {
  await installApiMocks(page);
  await login(page);

  await page.locator(".shuimu-nav").getByRole("link", { name: "探索商品" }).click();
  await page.getByRole("link", { name: "查看通勤轻薄外套详情" }).click();
  await expect(page.getByRole("heading", { name: "通勤轻薄外套" })).toBeVisible();

  await page.getByRole("button", { name: "黑色" }).click();
  await page.getByRole("button", { name: "L", exact: true }).click();
  await page.getByRole("button", { name: "加入购物袋" }).click();
  await page.getByTestId("confirm-action-submit").click();

  await page.locator(".shuimu-nav").getByRole("link", { name: /购物袋/ }).click();
  await page.getByTestId("checkout-submit").click();
  await page.getByRole("button", { name: "提交订单" }).click();
  await page.getByRole("button", { name: "演示支付" }).click();
  await page.getByRole("link", { name: "查看订单详情" }).click();

  await expect(page.getByRole("heading", { name: "订单 ORD-E2E-001" })).toBeVisible();
  await expect(page.getByText("已付款", { exact: true })).toBeVisible();
  await expect(page.getByText("商家正在准备商品")).toBeVisible();
});

for (const viewport of [
  { name: "桌面", width: 1440, height: 1000 },
  { name: "窄桌面", width: 900, height: 900 },
  { name: "手机", width: 390, height: 844 }
]) {
  test(`${viewport.name}端商品关键页面无横向溢出和操作遮挡`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await installApiMocks(page);
    await login(page);
    await page.goto("/app/products");
    await expect(page.getByRole("link", { name: "查看通勤轻薄外套详情" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
    if (viewport.width === 390) {
      const filterLayout = await page.locator(".catalog-filters").evaluate((element) => ({
        display: getComputedStyle(element).display,
        columns: getComputedStyle(element).gridTemplateColumns.split(" ").length
      }));
      expect(filterLayout).toEqual({ display: "grid", columns: 2 });
    }

    await page.getByRole("link", { name: "查看通勤轻薄外套详情" }).click();
    await expect(page.getByRole("heading", { name: "通勤轻薄外套" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);

    const addToCart = page.getByRole("button", { name: "加入购物袋" });
    await addToCart.scrollIntoViewIfNeeded();
    await expect(addToCart).toBeVisible();
    if (viewport.width === 390) {
      const [buttonBox, navBox] = await Promise.all([
        addToCart.boundingBox(),
        page.getByRole("navigation", { name: "移动端主导航" }).boundingBox()
      ]);
      expect(buttonBox && navBox && buttonBox.y + buttonBox.height <= navBox.y).toBeTruthy();
    }
  });
}
