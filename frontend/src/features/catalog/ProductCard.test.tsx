import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
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
    render(<MemoryRouter><ProductCard candidate={candidate} onAction={onAction} variant="featured" recommendationStatus="STRONG_MATCH" isAttributed /></MemoryRouter>);

    const card = screen.getByTestId("recommendation-card");
    expect(card).toHaveClass("product-card--featured");
    expect(card).toHaveAttribute("data-variant", "featured");
    expect(screen.getByText("AI 推荐")).toBeVisible();
    expect(screen.getByText("排序分 0.92")).toBeVisible();
    expect(screen.queryByText("AI 匹配 92%")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("add-to-cart-action"));
    expect(onAction).toHaveBeenCalledWith(expect.objectContaining({
      kind: "ADD_TO_CART",
      skuId: 2102,
      unitPrice: 299
    }));
  });

  it("does not generate AI labels for weak candidates", () => {
    const candidateWithoutAiFacts = { ...candidate, rankScore: undefined };
    const { container, rerender } = render(
      <MemoryRouter><ProductCard candidate={candidateWithoutAiFacts} onAction={vi.fn()} variant="supporting" recommendationStatus="BROWSE_FALLBACK" /></MemoryRouter>
    );

    expect(container.querySelector(".ai-match-badge")).not.toBeInTheDocument();
    expect(screen.queryByText("AI 首选")).not.toBeInTheDocument();

    rerender(<MemoryRouter><ProductCard candidate={candidateWithoutAiFacts} onAction={vi.fn()} variant="standard" /></MemoryRouter>);
    expect(container.querySelector(".ai-match-badge")).not.toBeInTheDocument();
  });

  it("links a recommendation to its product detail", () => {
    render(<MemoryRouter><ProductCard candidate={candidate} onAction={vi.fn()} /></MemoryRouter>);

    expect(screen.getByRole("link", { name: `查看${candidate.name}详情` })).toHaveAttribute(
      "href",
      `/app/products/${candidate.spuId}`
    );
  });
});
