import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import { CheckoutPage } from "./CheckoutPage";

describe("CheckoutPage", () => {
  afterEach(() => vi.restoreAllMocks());
  it("展示地址和由接口计算的应付金额", async () => {
    vi.spyOn(api, "addresses").mockResolvedValue([{ id: 1, recipientName: "林木", phone: "13800000000", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号", isDefault: true }]);
    vi.spyOn(api, "checkoutPreview").mockResolvedValue({ items: [], merchandiseAmount: 699, shippingAmount: 0, discountAmount: 0, payableAmount: 699, invalidReasons: [] });
    render(<MemoryRouter initialEntries={["/app/checkout?skuIds=11"]}><CheckoutPage onOrderCreated={vi.fn()} /></MemoryRouter>);
    expect(await screen.findByText(/文一路 88 号/)).toBeVisible();
    expect(screen.getAllByText("¥699.00")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "提交订单" })).toBeEnabled();
  });
});
