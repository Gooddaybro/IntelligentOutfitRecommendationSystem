import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CartPage } from "./CartPage";

const base = { id: 1, userId: 1, skuId: 11, spuId: 1, skuCode: "S", spuCode: "P", name: "通勤风衣", categoryName: "外套", salePrice: 599, quantity: 1, availableStock: 3 };

describe("CartPage", () => {
  it("禁选缺货商品并把有效 SKU 交给确认订单", () => {
    const onCheckout = vi.fn();
    render(<CartPage items={[base, { ...base, id: 2, skuId: 12, name: "缺货针织衫", availableStock: 0 }]} onItemsChange={vi.fn()} onCheckout={onCheckout} />);
    expect(screen.getByText("商品暂时缺货")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /去确认订单/ }));
    expect(onCheckout).toHaveBeenCalledWith([11]);
  });
});
