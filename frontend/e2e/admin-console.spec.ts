import { expect, test } from "@playwright/test";
import { installApiMocks } from "./fixtures/api";

const T = {
  username: "e2e_admin",
  password: "StrongPassword123!",
  dashboard: "\u6570\u636e\u6982\u89c8",
  products: "\u5546\u54c1\u7ba1\u7406",
  inventory: "SKU / \u5e93\u5b58",
  orders: "\u8ba2\u5355\u7ba1\u7406",
  users: "\u7528\u6237\u7ba1\u7406",
  analytics: "\u7ecf\u8425\u5206\u6790",
  audit: "\u64cd\u4f5c\u65e5\u5fd7",
  productName: "\u901a\u52e4\u8f7b\u8584\u5916\u5957",
  searchProduct: "\u641c\u7d22\u5546\u54c1",
  down: "\u4e0b\u67b6",
  confirmDown: "\u786e\u8ba4\u4e0b\u67b6",
  offShelf: "\u5df2\u4e0b\u67b6",
  searchInventory: "\u641c\u7d22\u5e93\u5b58",
  adjustInventory: "\u8c03\u6574\u5e93\u5b58",
  targetStock: "\u76ee\u6807\u5e93\u5b58",
  adjustReason: "\u8c03\u6574\u539f\u56e0",
  confirmAdjust: "\u786e\u8ba4\u8c03\u6574",
  stockReason: "\u5230\u8d27\u5165\u5e93",
  ship: "\u53d1\u8d27",
  carrier: "\u627f\u8fd0\u5546",
  trackingNo: "\u8fd0\u5355\u53f7",
  confirmShip: "\u786e\u8ba4\u53d1\u8d27",
  shipped: "\u5df2\u53d1\u8d27",
  disable: "\u7981\u7528",
  confirmDisable: "\u786e\u8ba4\u7981\u7528",
  disabled: "\u5df2\u7981\u7528",
  sf: "\u987a\u4e30\u901f\u8fd0"
} as const;

async function login(page: import("@playwright/test").Page, role: "ROLE_ADMIN" | "ROLE_USER" = "ROLE_ADMIN") {
  await installApiMocks(page, { role });
  await page.goto("/");
  await page.getByTestId("auth-username").fill(T.username);
  await page.getByTestId("auth-password").fill(T.password);
  await page.getByTestId("auth-submit").click();
  await expect(page).toHaveURL(/\/app\/home$/);
}

test("admin console supports product, inventory, order, user, analytics and audit flows", async ({ page }) => {
  await login(page);

  await page.goto("/admin");
  await expect(page.getByTestId("admin-shell")).toBeVisible();
  await expect(page.getByRole("heading", { name: T.dashboard })).toBeVisible();

  await page.goto("/admin/products");
  await expect(page.getByRole("heading", { name: T.products })).toBeVisible();
  await page.getByLabel(T.searchProduct).fill(T.productName);
  const productRow = page.getByRole("row").filter({ hasText: T.productName });
  await expect(productRow).toBeVisible();
  await productRow.getByRole("button", { name: T.down }).click();
  await page.getByRole("button", { name: T.confirmDown }).click();
  await expect(productRow).toContainText(T.offShelf);

  await page.goto("/admin/inventory");
  await expect(page.getByRole("heading", { name: T.inventory })).toBeVisible();
  await page.getByLabel(T.searchInventory).fill("SKU-ADMIN-001");
  const skuRow = page.getByRole("row").filter({ hasText: "SKU-ADMIN-001" });
  await expect(skuRow).toBeVisible();
  await skuRow.getByRole("button", { name: T.adjustInventory }).click();
  await page.getByLabel(T.targetStock).fill("12");
  await page.getByLabel(T.adjustReason).fill(T.stockReason);
  await page.getByRole("button", { name: T.confirmAdjust }).click();
  await expect(skuRow).toContainText("12");

  await page.goto("/admin/orders");
  await expect(page.getByRole("heading", { name: T.orders })).toBeVisible();
  const orderRow = page.getByRole("row").filter({ hasText: "ORD-ADMIN-001" });
  await orderRow.getByRole("button", { name: T.ship }).click();
  await page.getByLabel(T.carrier).fill(T.sf);
  await page.getByLabel(T.trackingNo).fill("SFADMIN001");
  await page.getByRole("button", { name: T.confirmShip }).click();
  await expect(orderRow).toContainText(T.shipped);

  await page.goto("/admin/users");
  await expect(page.getByRole("heading", { name: T.users })).toBeVisible();
  const userRow = page.getByRole("row").filter({ hasText: "admin_user" });
  await userRow.getByRole("button", { name: T.disable }).click();
  await page.getByRole("button", { name: T.confirmDisable }).click();
  await expect(userRow).toContainText(T.disabled);

  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: T.analytics })).toBeVisible();
  await expect(page.getByText(T.productName)).toBeVisible();

  await page.goto("/admin/audit-logs");
  await expect(page.getByRole("heading", { name: T.audit })).toBeVisible();
  await expect(page.getByText("SHIP_ORDER")).toBeVisible();
});

test("non-admin users are redirected away from admin console", async ({ page }) => {
  await login(page, "ROLE_USER");
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/app\/home$/);
});

for (const viewport of [
  { name: "desktop", width: 1440, height: 1000 },
  { name: "tablet", width: 900, height: 900 },
  { name: "mobile", width: 390, height: 844 }
]) {
  test(`admin pages avoid page-level horizontal overflow on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await login(page);
    for (const path of ["/admin", "/admin/products", "/admin/inventory", "/admin/orders", "/admin/users", "/admin/analytics", "/admin/audit-logs"]) {
      await page.goto(path);
      await expect(page.getByTestId("admin-shell")).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
    }
  });
}
