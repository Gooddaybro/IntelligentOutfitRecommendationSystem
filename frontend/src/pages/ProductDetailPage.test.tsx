import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import { ProductDetailPage } from "./ProductDetailPage";

describe("ProductDetailPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("选择完整且有库存的 SKU 后才允许购买", async () => {
    vi.spyOn(api, "productDetail").mockResolvedValue({ spuId: 1001, spuCode: "PUFFER", name: "轻量通勤羽绒服", categoryName: "外套", minPrice: 699, maxPrice: 699, description: "轻暖通勤", mainImageUrl: "/coat.jpg" });
    vi.spyOn(api, "recommendationCandidates").mockResolvedValue([
      { spuId: 1001, skuId: 2001, spuCode: "PUFFER", name: "轻量通勤羽绒服", categoryName: "外套", color: "米白", size: "M", salePrice: 699, availableStock: 8 }
    ]);
    const onAction = vi.fn();

    render(
      <MemoryRouter initialEntries={["/app/products/1001"]}>
        <Routes><Route path="/app/products/:spuId" element={<ProductDetailPage onAction={onAction} />} /></Routes>
      </MemoryRouter>
    );

    expect(await screen.findByRole("heading", { name: "轻量通勤羽绒服" })).toBeVisible();
    expect(screen.getByRole("button", { name: "加入购物袋" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "米白" }));
    fireEvent.click(screen.getByRole("button", { name: "M" }));
    expect(screen.getByRole("button", { name: "加入购物袋" })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: "加入购物袋" }));
    expect(onAction).toHaveBeenCalledWith(expect.objectContaining({ kind: "ADD_TO_CART", skuId: 2001 }));
  });

  it("收藏按钮写入收藏并反馈状态", async () => {
    vi.spyOn(api, "productDetail").mockResolvedValue({ spuId: 1001, spuCode: "PUFFER", name: "轻量通勤羽绒服", categoryName: "外套", minPrice: 699, maxPrice: 699 });
    vi.spyOn(api, "recommendationCandidates").mockResolvedValue([]);
    vi.spyOn(api, "favorites").mockResolvedValue([]);
    const add = vi.spyOn(api, "addFavorite").mockResolvedValue([]);
    render(<MemoryRouter initialEntries={["/app/products/1001"]}><Routes><Route path="/app/products/:spuId" element={<ProductDetailPage onAction={vi.fn()} />} /></Routes></MemoryRouter>);
    const button = await screen.findByRole("button", { name: "收藏商品" });
    fireEvent.click(button);
    await screen.findByRole("button", { name: "已收藏" });
    expect(add).toHaveBeenCalledWith(1001);
  });
});
