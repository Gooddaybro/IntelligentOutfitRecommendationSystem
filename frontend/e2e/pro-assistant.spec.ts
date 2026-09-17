import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { installApiMocks } from "./fixtures/api";

async function login(page: Page) {
  await page.goto("/");
  await page.getByTestId("auth-username").fill("e2e_user");
  await page.getByTestId("auth-password").fill("StrongPassword123!");
  await page.getByTestId("auth-submit").click();
  await expect(page).toHaveURL(/\/app\/home$/);
}

function sseEvent(name: string, payload: unknown) {
  return `event: ${name}\ndata: ${JSON.stringify(payload)}\n\n`;
}

test("Lite/Pro 可切换，Pro 按进度完成库存核验后展示 B 商品并跳转详情", async ({ page }) => {
  await installApiMocks(page);

  let proRequest: Record<string, unknown> | undefined;
  await page.route("**/api/assistant/v2/chat/stream", async (route) => {
    proRequest = route.request().postDataJSON() as Record<string, unknown>;
    const runId = "run-pro-e2e";
    const body = [
      sseEvent("meta", {
        thread_id: "thread-pro-e2e",
        request_id: "request-pro-e2e",
        run_id: runId,
        agent_mode: "pro"
      }),
      sseEvent("progress", {
        run_id: runId,
        sequence: 1,
        tool: "check_availability",
        stage: "started",
        message: "正在核验库存"
      }),
      sseEvent("progress", {
        run_id: runId,
        sequence: 2,
        tool: "check_availability",
        stage: "completed",
        message: "正在核验库存"
      }),
      sseEvent("token", { content: "A 商品无货，已根据库存结果改选 B。" }),
      sseEvent("done", {
        contract_version: "assistant-v2",
        agent_mode: "pro",
        thread_id: "thread-pro-e2e",
        request_id: "request-pro-e2e",
        run_id: runId,
        answer: "A 商品无货，已根据库存结果改选 B。",
        product_refs: [{
          spu_id: 1002,
          sku_id: 2102,
          reason: "库存核验后选择 B 商品。",
          basis: "product_chart",
          evidence_ids: ["ev-stock-b"]
        }],
        recommended_items: [{
          spu_id: 1002,
          sku_id: 2102,
          name: "核验后改选 B 外套",
          sale_price: "299.00",
          main_image_url: "/images/products/jacket-commute-main.svg",
          color: "黑色",
          size: "L",
          available_stock: 4,
          reason: "库存核验后选择 B 商品。",
          rank_score: 0.96,
          basis: "product_chart"
        }],
        recommended_spu_ids: [1002],
        recommendation_id: "rec-pro-e2e",
        recommendation_status: "STRONG_MATCH",
        requirements: [{
          id: "budget",
          text: "预算不超过 300 元",
          status: "satisfied",
          evidence_ids: ["ev-stock-b"]
        }]
      })
    ].join("");

    await route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body
    });
  });

  await login(page);
  await page.locator(".shuimu-nav").getByRole("link", { name: "AI 造型师" }).click();
  await expect(page.getByTestId("agent-mode-selector")).toHaveValue("lite");

  await page.getByTestId("ai-chat-input").fill("先给我一件通勤外套");
  await page.getByTestId("ai-chat-submit").click();
  await expect(page.getByTestId("chat-message-assistant").last()).toContainText("通勤建议优先看黑色轻薄外套。");

  await page.getByTestId("agent-mode-selector").selectOption("pro");
  await expect(page.getByTestId("agent-mode-hint")).toContainText("动态调用工具");
  await page.getByTestId("ai-chat-input").fill("如果 A 缺货，请核验库存后换成 B");
  await page.getByTestId("ai-chat-submit").click();

  await expect.poll(() => proRequest?.agentMode).toBe("pro");
  await expect(page.getByTestId("assistant-progress")).toContainText("正在核验库存");
  await expect(page.getByTestId("assistant-progress-item")).toHaveCount(2);
  await expect(page.getByTestId("chat-message-assistant").last()).toContainText("改选 B");

  const card = page.getByTestId("recommendation-card").first();
  await expect(card).toContainText("核验后改选 B 外套");
  await expect(card.getByTestId("recommendation-reason")).toContainText("库存核验后选择 B");
  await expect(card.getByText("AI 推荐")).toBeVisible();
  await card.getByRole("link").click();
  await expect(page).toHaveURL(/\/app\/products\/1002$/);
});
