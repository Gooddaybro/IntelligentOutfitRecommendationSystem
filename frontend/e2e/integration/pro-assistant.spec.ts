import { expect, test, type Page } from "@playwright/test";

async function registerAndLogin(page: Page) {
  const unique = `${Date.now()}${Math.floor(Math.random() * 10000)}`;
  const username = `pro_integration_${unique}`;
  const password = "StrongPassword123!";

  await page.goto("/");
  await page.getByTestId("auth-register-mode").click();
  await page.getByTestId("auth-username").fill(username);
  await page.getByTestId("auth-password").fill(password);
  await page.getByTestId("auth-email").fill(`${username}@example.com`);
  await page.getByTestId("auth-submit").click();
  await expect(page).toHaveURL(/\/app\/home$/, { timeout: 30_000 });
}

test("Java→Python→Java Pro 流在客户端取消后及时结束", async ({ page }) => {
  test.skip(process.env.RUN_PRO_INTEGRATION !== "true", "需要显式启用 Docker Pro 集成环境");
  await registerAndLogin(page);

  const result = await page.evaluate(async () => {
    const controller = new AbortController();
    const response = await fetch("/api/assistant/v2/chat/stream", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "请推荐一件预算 300 元以内的通勤外套",
        agentMode: "pro",
        style: "commute",
        budgetMax: 300
      }),
      signal: controller.signal
    });
    const reader = response.body?.getReader();
    const firstChunk = reader ? await reader.read() : { value: undefined };
    controller.abort();
    await reader?.cancel();
    return {
      status: response.status,
      contentType: response.headers.get("content-type"),
      firstChunk: new TextDecoder().decode(firstChunk.value ?? new Uint8Array())
    };
  });

  expect(result.status).toBe(200);
  expect(result.contentType).toContain("text/event-stream");
  expect(result.firstChunk).toContain("event: meta");
});
