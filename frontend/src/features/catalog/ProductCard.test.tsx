import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProductCard } from "./ProductCard";

const candidate = {
  spuId: 1002,
  skuId: 2102,
  spuCode: "JACKET_COMMUTE_001",
  name: "通勤轻薄外套",
  categoryName: "外套",
  salePrice: 299,
  rankScore: 0.92
};

describe("ProductCard", () => {
  it("marks an editorial featured card without changing the add-to-cart action", () => {
    const onAction = vi.fn();
    render(<ProductCard candidate={candidate} onAction={onAction} variant="featured" />);

    const card = screen.getByTestId("recommendation-card");
    expect(card).toHaveClass("product-card--featured");
    expect(card).toHaveAttribute("data-variant", "featured");
    expect(screen.getByText("AI 首选")).toBeVisible();

    fireEvent.click(screen.getByTestId("add-to-cart-action"));
    expect(onAction).toHaveBeenCalledWith(expect.objectContaining({
      kind: "ADD_TO_CART",
      skuId: 2102,
      unitPrice: 299
    }));
  });
});
