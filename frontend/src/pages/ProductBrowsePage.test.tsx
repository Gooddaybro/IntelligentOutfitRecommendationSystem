import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import { ProductBrowsePage } from "./ProductBrowsePage";

describe("ProductBrowsePage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("展示分类、紧凑筛选和单一商品网格", async () => {
    vi.spyOn(api, "searchProducts").mockResolvedValue([]);
    vi.spyOn(api, "recommendationCandidates").mockResolvedValue([]);

    render(<MemoryRouter><ProductBrowsePage onAction={vi.fn()} /></MemoryRouter>);

    expect(await screen.findByRole("heading", { name: "探索适合你的穿搭" })).toBeVisible();
    expect(screen.getByRole("button", { name: "外套" })).toBeVisible();
    expect(screen.getByLabelText("商品排序")).toBeVisible();
    expect(screen.getByTestId("catalog-product-grid")).toBeVisible();
    expect(screen.queryByText("候选商品卡片")).not.toBeInTheDocument();
  });

  it("关键字、风格和价格筛选共同影响商品结果", async () => {
    vi.spyOn(api, "searchProducts").mockResolvedValue([]);
    vi.spyOn(api, "recommendationCandidates").mockResolvedValue([
      { spuId: 1, skuId: 11, spuCode: "COAT", name: "浅卡其通勤风衣", categoryName: "外套", styleTags: "通勤,自然", size: "M", salePrice: 599 },
      { spuId: 2, skuId: 12, spuCode: "SHIRT", name: "基础牛津衬衫", categoryName: "上装", styleTags: "基础,简约", size: "L", salePrice: 269 }
    ]);
    render(<MemoryRouter><ProductBrowsePage onAction={vi.fn()} /></MemoryRouter>);
    expect(await screen.findByText("浅卡其通勤风衣")).toBeVisible();
    fireEvent.change(screen.getByLabelText("风格筛选"), { target: { value: "简约" } });
    await waitFor(() => expect(screen.queryByText("浅卡其通勤风衣")).not.toBeInTheDocument());
    expect(screen.getByText("基础牛津衬衫")).toBeVisible();
    fireEvent.change(screen.getByLabelText("价格筛选"), { target: { value: "300-600" } });
    expect(screen.getByText("没有找到符合当前条件的商品。")).toBeVisible();
  });

  it("分类筛选即使在接口返回混合候选时也只展示当前品类", async () => {
    vi.spyOn(api, "searchProducts").mockResolvedValue([]);
    vi.spyOn(api, "recommendationCandidates").mockResolvedValue([
      { spuId: 1, skuId: 11, spuCode: "COAT", name: "浅卡其通勤风衣", categoryName: "外套", salePrice: 599 },
      { spuId: 2, skuId: 12, spuCode: "SHIRT", name: "基础牛津衬衫", categoryName: "上装", salePrice: 269 }
    ]);
    render(<MemoryRouter><ProductBrowsePage onAction={vi.fn()} /></MemoryRouter>);
    expect(await screen.findByText("基础牛津衬衫")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "外套" }));
    await waitFor(() => expect(screen.queryByText("基础牛津衬衫")).not.toBeInTheDocument());
    expect(screen.getByText("浅卡其通勤风衣")).toBeVisible();
  });
});
