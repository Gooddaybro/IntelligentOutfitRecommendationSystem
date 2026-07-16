import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { HomePage } from "./HomePage";

describe("HomePage", () => {
  it("优先展示 AI、商品分类和购物状态", () => {
    render(
      <MemoryRouter>
        <HomePage
          username="林木"
          cartCount={2}
          recommendations={[{
            spuId: 1001,
            skuId: 2001,
            spuCode: "COAT_001",
            name: "浅卡其通勤风衣",
            categoryName: "外套",
            salePrice: 699
          }]}
        />
      </MemoryRouter>
    );

    expect(screen.getByRole("heading", { name: /我的穿搭空间/ })).toBeVisible();
    expect(screen.getByRole("link", { name: /和 AI 一起挑选/ })).toHaveAttribute("href", "/app/ai");
    expect(screen.getByRole("link", { name: /探索全部商品/ })).toHaveAttribute("href", "/app/products");
    expect(screen.getByText("购物袋 2 件待决定")).toBeVisible();
    expect(screen.getByRole("link", { name: /浅卡其通勤风衣/ })).toHaveAttribute("href", "/app/products/1001");
  });

  it("同一商品的多个 SKU 在首页只展示一次", () => {
    const recommendation = { spuId: 1001, spuCode: "COAT_001", name: "轻量通勤羽绒服", categoryName: "外套", salePrice: 699 };
    render(<MemoryRouter><HomePage username="林木" cartCount={0} recommendations={[{ ...recommendation, skuId: 2001 }, { ...recommendation, skuId: 2002 }]} /></MemoryRouter>);
    expect(screen.getAllByRole("link", { name: /轻量通勤羽绒服/ })).toHaveLength(1);
  });
});
