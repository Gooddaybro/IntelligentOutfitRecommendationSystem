import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../../shared/api/client";
import type { AdminProduct } from "../../shared/api/adminTypes";
import { AdminProductsPage } from "./AdminProductsPage";

const product: AdminProduct = { spuId: 1002, spuCode: "TRENCH", name: "浅卡其通勤风衣", categoryId: 2, categoryName: "外套", minPrice: 599, maxPrice: 599, skuCount: 1, totalStock: 12, status: "ON_SALE", createdAt: "2026-07-01" };

describe("AdminProductsPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("查询商品并在二次确认后更新上下架状态", async () => {
    vi.spyOn(api, "adminProducts").mockResolvedValue([product]);
    const setStatus = vi.spyOn(api, "adminSetProductStatus").mockResolvedValue({ ...product, status: "OFF_SHELF" });
    render(<MemoryRouter><AdminProductsPage /></MemoryRouter>);
    expect(await screen.findByText("浅卡其通勤风衣")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "下架" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("下架后用户将无法购买这件商品");
    fireEvent.click(screen.getByRole("button", { name: "确认下架" }));
    expect(await screen.findByText("已下架")).toBeVisible();
    expect(setStatus).toHaveBeenCalledWith(1002, "OFF_SHELF");
  });
});
