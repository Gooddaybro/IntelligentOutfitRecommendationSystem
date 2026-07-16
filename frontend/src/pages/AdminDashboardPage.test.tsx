import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import { AdminDashboardPage } from "./AdminDashboardPage";

describe("AdminDashboardPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("展示接口返回的运营指标和统计口径", async () => {
    vi.spyOn(api, "adminOverview").mockResolvedValue({
      onSaleProducts: 6,
      skuCount: 7,
      lowStockCount: 2,
      pendingShipmentOrders: 3,
      afterSaleOrders: 1,
      orderCount: 18,
      paidAmount: 12680,
      rangeLabel: "最近 30 天",
      trend: [{ label: "07-16", amount: 2680 }],
      hotProducts: [{ spuId: 1002, name: "浅卡其通勤风衣", sales: 12 }]
    });
    render(<AdminDashboardPage />);
    expect(screen.getByRole("heading", { name: "数据概览" })).toBeVisible();
    expect(await screen.findByText("¥12,680.00")).toBeVisible();
    expect(screen.getByText("7")).toBeVisible();
    expect(screen.getByText("浅卡其通勤风衣")).toBeVisible();
    expect(screen.getAllByText("最近 30 天").length).toBeGreaterThan(0);
    expect(screen.queryByText("待接入真实数据")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "新增商品" })).toHaveAttribute("href", "/admin/products/new");
  });
});
