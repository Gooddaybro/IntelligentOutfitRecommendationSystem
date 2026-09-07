import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import { FavoritesPage } from "./FavoritesPage";

describe("FavoritesPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("保留下架收藏的删除入口并明确提示暂不可购买", async () => {
    vi.spyOn(api, "favorites").mockResolvedValue([{
      spuId: 1001,
      name: "基础款纯棉T恤",
      categoryName: "上衣",
      salePrice: 99,
      availabilityStatus: "unavailable",
      totalAvailableStock: 0
    }]);
    const removeFavorite = vi.spyOn(api, "removeFavorite").mockResolvedValue([]);

    render(<MemoryRouter><FavoritesPage /></MemoryRouter>);

    expect(await screen.findByText("暂不可购买")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "取消收藏基础款纯棉T恤" }));
    await waitFor(() => expect(removeFavorite).toHaveBeenCalledWith(1001));
  });
});
