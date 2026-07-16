import { render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AdminAnalytics, AdminAuditLog } from "../../shared/api/adminTypes";
import { api } from "../../shared/api/client";
import { AdminAnalyticsPage } from "./AdminAnalyticsPage";
import { AdminAuditLogsPage } from "./AdminAuditLogsPage";

const RANGE = "\u6700\u8fd1 30 \u5929";
const FUNNEL = "\u8f6c\u5316\u6f0f\u6597";
const HOT_PRODUCT = "\u901a\u52e4\u8f7b\u8584\u5916\u5957";
const LOG_ACTION = "SHIP_ORDER";
const ADMIN = "\u8fd0\u8425\u7ba1\u7406\u5458";
const SUCCESS = "\u6210\u529f";
const EDIT = "\u7f16\u8f91";
const DELETE = "\u5220\u9664";

const analytics: AdminAnalytics = {
  rangeLabel: RANGE,
  orderCount: 42,
  paidAmount: 38990,
  funnel: { exposed: 1000, clicked: 320, cartAdded: 88, purchased: 26, definition: "\u66dd\u5149\u5230\u6210\u4ea4\u7684\u8f6c\u5316\u53e3\u5f84" },
  trend: [
    { label: "07-14", orderCount: 11, paidAmount: 9200 },
    { label: "07-15", orderCount: 15, paidAmount: 13400 },
    { label: "07-16", orderCount: 16, paidAmount: 16390 }
  ],
  hotProducts: [{ spuId: 1002, name: HOT_PRODUCT, sales: 18, paidAmount: 5382 }],
  categoryTrend: [{ categoryName: "\u5916\u5957", sales: 18 }]
};

const logs: AdminAuditLog[] = [
  { id: 1, operator: ADMIN, action: LOG_ACTION, targetType: "ORDER", targetId: "ORD-20260716-001", result: "SUCCESS", summary: "SF123456789", createdAt: "2026-07-16T10:00:00Z" }
];

describe("AdminAnalyticsPage and AdminAuditLogsPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("renders analytics facts, conversion funnel, hot products and read-only audit logs", async () => {
    vi.spyOn(api, "adminAnalytics").mockResolvedValue(analytics);
    vi.spyOn(api, "adminAuditLogs").mockResolvedValue(logs);

    const { unmount } = render(<AdminAnalyticsPage />);
    expect((await screen.findAllByText(RANGE))[0]).toBeVisible();
    expect(screen.getByText("42")).toBeVisible();
    expect(screen.getByText(/38,990/)).toBeVisible();
    expect(screen.getByText(FUNNEL)).toBeVisible();
    expect(screen.getByText(/1,000/)).toBeVisible();
    expect(screen.getByText(HOT_PRODUCT)).toBeVisible();
    unmount();

    render(<AdminAuditLogsPage />);
    const row = (await screen.findByText(LOG_ACTION)).closest("tr");
    expect(row).not.toBeNull();
    expect(within(row!).getByText(ADMIN)).toBeVisible();
    expect(within(row!).getByText(SUCCESS)).toBeVisible();
    expect(screen.queryByRole("button", { name: EDIT })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: DELETE })).not.toBeInTheDocument();
  });
});
