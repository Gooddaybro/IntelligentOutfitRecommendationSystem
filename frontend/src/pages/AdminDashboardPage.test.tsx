import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AdminDashboardPage } from "./AdminDashboardPage";

describe("AdminDashboardPage", () => {
  it("展示后台关键运营入口但不伪造指标", () => {
    render(<AdminDashboardPage />);
    expect(screen.getByRole("heading", { name: "数据概览" })).toBeVisible();
    expect(screen.getAllByText("待接入真实数据")).toHaveLength(4);
    expect(screen.getByRole("link", { name: "新增商品" })).toHaveAttribute("href", "/admin/products/new");
  });
});
