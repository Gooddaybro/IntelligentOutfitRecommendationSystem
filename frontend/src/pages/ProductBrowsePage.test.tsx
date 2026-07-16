import { render, screen } from "@testing-library/react";
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
});
