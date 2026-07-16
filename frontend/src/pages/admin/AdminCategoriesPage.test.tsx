import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../../shared/api/client";
import { AdminCategoriesPage } from "./AdminCategoriesPage";

describe("AdminCategoriesPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("停用分类前说明关联商品影响范围", async () => {
    const category = { id: 2, name: "外套", parentId: null, level: 1 as const, sortOrder: 2, enabled: true, productCount: 2 };
    vi.spyOn(api, "adminCategories").mockResolvedValue([category]);
    const update = vi.spyOn(api, "adminUpdateCategory").mockResolvedValue({ ...category, enabled: false });
    render(<AdminCategoriesPage />);
    expect(await screen.findByText("外套")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "停用" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("关联 2 件商品");
    fireEvent.click(screen.getByRole("button", { name: "确认停用" }));
    expect(await screen.findByText("已停用")).toBeVisible();
    expect(update).toHaveBeenCalledWith(expect.objectContaining({ id: 2, enabled: false }));
  });
});
