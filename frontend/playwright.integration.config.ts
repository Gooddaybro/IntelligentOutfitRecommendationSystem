import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e/integration",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 120_000,
  reporter: [["list"], ["html", { outputFolder: "playwright-report/integration", open: "never" }]],
  use: {
    baseURL: process.env.GOLDEN_PATH_BASE_URL ?? "http://127.0.0.1:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure"
  },
  outputDir: "test-results/integration",
  projects: [
    {
      name: "chromium-integration",
      use: { ...devices["Desktop Chrome"] }
    }
  ]
});
