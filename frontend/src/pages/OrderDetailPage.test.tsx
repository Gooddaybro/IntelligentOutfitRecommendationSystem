import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import { OrderDetailPage } from "./OrderDetailPage";

describe("OrderDetailPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("待付款订单显示地址、金额和取消入口", async () => {
    vi.spyOn(api, "order").mockResolvedValue({ orderNo: "DEMO-1", status: "PENDING_PAYMENT", totalAmount: 699, createdAt: "2026-07-16T10:00:00Z", items: [{ skuId: 1, spuId: 1, skuCode: "S", spuCode: "P", productName: "轻量通勤羽绒服", categoryName: "外套", salePrice: 699, quantity: 1, lineAmount: 699 }], address: { id: 1, recipientName: "林木", phone: "13800000000", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号" } });
    const cancel = vi.spyOn(api, "cancelOrder").mockResolvedValue({ orderNo: "DEMO-1", status: "CANCELLED", totalAmount: 699, createdAt: "2026-07-16T10:00:00Z", items: [] });
    render(<MemoryRouter initialEntries={["/app/orders/DEMO-1"]}><Routes><Route path="/app/orders/:orderNo" element={<OrderDetailPage />} /></Routes></MemoryRouter>);
    expect(await screen.findByRole("heading", { name: /订单 DEMO-1/ })).toBeVisible();
    expect(screen.getByText(/文一路 88 号/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "取消订单" }));
    await waitFor(() => expect(cancel).toHaveBeenCalledWith("DEMO-1"));
    expect(screen.getByText("已取消")).toBeVisible();
  });
});
